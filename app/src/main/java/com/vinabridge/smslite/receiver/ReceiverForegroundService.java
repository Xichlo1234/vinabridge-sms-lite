package com.vinabridge.smslite.receiver;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import com.vinabridge.smslite.R;
import com.vinabridge.smslite.VinaBridgeApp;
import com.vinabridge.smslite.firebase.FirebaseDbRestClient;
import com.vinabridge.smslite.gateway.GatewaySyncService;
import com.vinabridge.smslite.model.FirebaseSmsRecord;
import com.vinabridge.smslite.model.SmsPayload;
import com.vinabridge.smslite.security.CryptoManager;
import com.vinabridge.smslite.ui.ReceiverActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Foreground Service for the Receiver device.
 * Maintains real-time SSE stream from Firebase, decrypts SMS, and posts secure notifications.
 */
public class ReceiverForegroundService extends Service {
    public static final String ACTION_NEW_SMS = "com.vinabridge.smslite.ACTION_NEW_SMS";
    public static final String EXTRA_SMS_PAYLOAD = "extra_sms_payload";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;

    private Thread streamThread;
    private final FirebaseDbRestClient.AtomicBoolean stopSignal = new FirebaseDbRestClient.AtomicBoolean(false);
    private final CryptoManager cryptoManager = new CryptoManager();
    private final Set<String> notifiedMessageIds = Collections.synchronizedSet(new HashSet<>());

    public static void startListening(Context context) {
        Intent intent = new Intent(context, ReceiverForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopListening(Context context) {
        Intent intent = new Intent(context, ReceiverForegroundService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification("Đang kết nối tới Firebase..."));
        startStreamWorker();
    }

    private void startStreamWorker() {
        stopSignal.set(false);
        streamThread = new Thread(() -> {
            int backoffSec = 3;
            while (!stopSignal.get()) {
                FirebaseDbRestClient.listenStreamBlocking(this, new FirebaseDbRestClient.StreamListener() {
                    @Override
                    public void onConnected() {
                        updateForegroundNotification("Đang lắng nghe tin nhắn SMS theo thời gian thực");
                    }

                    @Override
                    public void onDisconnected(String reason) {
                        updateForegroundNotification("Mất kết nối (" + (reason != null ? reason : "Lỗi mạng") + "). Đang thử lại...");
                    }

                    @Override
                    public void onRecordReceived(FirebaseSmsRecord record) {
                        handleIncomingRecord(record);
                    }
                }, stopSignal);

                if (stopSignal.get()) break;

                // Wait with backoff before reconnecting
                try {
                    Thread.sleep(backoffSec * 1000L);
                    if (backoffSec < 30) backoffSec *= 2;
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "receiver-sse-stream");
        streamThread.start();
    }

    private void handleIncomingRecord(FirebaseSmsRecord record) {
        if (record == null || record.getId() == null) return;
        if (notifiedMessageIds.contains(record.getId())) return; // Deduplicate

        SharedPreferences prefs = GatewaySyncService.preferences(this);
        String passphrase = prefs.getString(GatewaySyncService.PASSPHRASE, "");
        if (passphrase.isEmpty()) return;

        try {
            String decryptedJson = cryptoManager.decrypt(record.getEncryptedPayload(), passphrase);
            SmsPayload payload = SmsPayload.fromJson(decryptedJson);
            if (payload != null) {
                notifiedMessageIds.add(record.getId());

                // Broadcast to active UI
                Intent broadcast = new Intent(ACTION_NEW_SMS);
                broadcast.putExtra(EXTRA_SMS_PAYLOAD, payload.toJson());
                broadcast.setPackage(getPackageName());
                sendBroadcast(broadcast);

                // Show secure Notification
                postSmsAlertNotification(payload);
            }
        } catch (Exception ignored) {
            // Decryption failure (tampered ciphertext or wrong passphrase)
        }
    }

    private void postSmsAlertNotification(SmsPayload payload) {
        Intent openIntent = new Intent(this, ReceiverActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        // Public version for lock screen (masks sensitive OTP)
        Notification publicNotif = new NotificationCompat.Builder(this, VinaBridgeApp.CHANNEL_SMS_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.receiver_new_sms_notif_title))
                .setContentText(getString(R.string.receiver_new_sms_notif_hidden, payload.getSender()))
                .build();

        // Main private notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, VinaBridgeApp.CHANNEL_SMS_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("SMS mới từ: " + payload.getSender())
                .setContentText("Mở để xem & sao chép nội dung/OTP")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotif);

        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(payload.getId().hashCode(), builder.build());
        }
    }

    private Notification buildForegroundNotification(String statusText) {
        Intent intent = new Intent(this, ReceiverActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        return new NotificationCompat.Builder(this, VinaBridgeApp.CHANNEL_SERVICE_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.receiver_foreground_notif_title))
                .setContentText(statusText)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    private void updateForegroundNotification(String statusText) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(statusText));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSignal.set(true);
        if (streamThread != null) {
            streamThread.interrupt();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
