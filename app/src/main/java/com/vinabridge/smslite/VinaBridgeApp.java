package com.vinabridge.smslite;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class VinaBridgeApp extends Application {
    public static final String CHANNEL_SERVICE_ID = "vinabridge_service_channel";
    public static final String CHANNEL_SMS_ID = "vinabridge_sms_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Channel for persistent Foreground Service
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_SERVICE_ID,
                        "Dịch vụ Đồng bộ SMS",
                        NotificationManager.IMPORTANCE_LOW
                );
                serviceChannel.setDescription("Duy trì kết nối đồng bộ tin nhắn SMS qua Firebase");
                manager.createNotificationChannel(serviceChannel);

                // Channel for new incoming SMS alerts
                NotificationChannel smsChannel = new NotificationChannel(
                        CHANNEL_SMS_ID,
                        "Thông báo SMS Mới",
                        NotificationManager.IMPORTANCE_HIGH
                );
                smsChannel.setDescription("Hiển thị thông báo khi có tin nhắn SMS mới đồng bộ về");
                smsChannel.enableVibration(true);
                manager.createNotificationChannel(smsChannel);
            }
        }
    }
}
