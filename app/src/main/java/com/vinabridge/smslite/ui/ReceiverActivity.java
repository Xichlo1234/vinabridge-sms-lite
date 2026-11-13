package com.vinabridge.smslite.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.vinabridge.smslite.R;
import com.vinabridge.smslite.firebase.FirebaseDbRestClient;
import com.vinabridge.smslite.gateway.GatewaySyncService;
import com.vinabridge.smslite.model.FirebaseSmsRecord;
import com.vinabridge.smslite.model.SmsPayload;
import com.vinabridge.smslite.receiver.ReceiverForegroundService;
import com.vinabridge.smslite.security.CryptoManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReceiverActivity extends AppCompatActivity {
    private static final int PERMISSION_REQ_NOTIF = 202;

    private TextView tvStreamStatus;
    private TextView tvEmpty;
    private ProgressBar pbLoading;
    private SmsAdapter adapter;
    private final CryptoManager cryptoManager = new CryptoManager();

    private final BroadcastReceiver smsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ReceiverForegroundService.ACTION_NEW_SMS.equals(intent.getAction())) {
                String payloadJson = intent.getStringExtra(ReceiverForegroundService.EXTRA_SMS_PAYLOAD);
                if (payloadJson != null) {
                    SmsPayload payload = SmsPayload.fromJson(payloadJson);
                    if (payload != null) {
                        runOnUiThread(() -> {
                            adapter.addMessageToTop(payload);
                            tvEmpty.setVisibility(View.GONE);
                        });
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);

        tvStreamStatus = findViewById(R.id.tv_stream_status);
        tvEmpty = findViewById(R.id.tv_empty);
        pbLoading = findViewById(R.id.pb_loading);

        RecyclerView recyclerView = findViewById(R.id.rv_messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter();
        recyclerView.setAdapter(adapter);

        Button btnManualSync = findViewById(R.id.btn_manual_sync);
        btnManualSync.setOnClickListener(v -> performManualSync());

        checkNotificationPermission();

        // Start Foreground Service to listen in real time
        ReceiverForegroundService.startListening(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ReceiverForegroundService.ACTION_NEW_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsBroadcastReceiver, filter);
        }
        performManualSync();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(smsBroadcastReceiver);
        } catch (Exception ignored) {}
    }

    private void performManualSync() {
        pbLoading.setVisibility(View.VISIBLE);
        tvStreamStatus.setText("Đang tải dữ liệu từ Firebase...");

        FirebaseDbRestClient.fetchAllRecords(this, new FirebaseDbRestClient.SnapshotCallback() {
            @Override
            public void onSuccess(List<FirebaseSmsRecord> records) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    tvStreamStatus.setText("Trạng thái: Đã kết nối & đồng bộ");

                    SharedPreferences prefs = GatewaySyncService.preferences(ReceiverActivity.this);
                    String passphrase = prefs.getString(GatewaySyncService.PASSPHRASE, "");

                    if (passphrase.isEmpty()) {
                        Toast.makeText(ReceiverActivity.this, "Vui lòng nhập mã khóa ghép cặp để giải mã!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    List<SmsPayload> decryptedList = new ArrayList<>();
                    for (FirebaseSmsRecord rec : records) {
                        try {
                            String plain = cryptoManager.decrypt(rec.getEncryptedPayload(), passphrase);
                            SmsPayload p = SmsPayload.fromJson(plain);
                            if (p != null) {
                                decryptedList.add(p);
                            }
                        } catch (Exception ignored) {
                            // Can occur if record was encrypted with different key
                        }
                    }

                    // Sort newest first
                    Collections.sort(decryptedList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                    adapter.setMessages(decryptedList);
                    tvEmpty.setVisibility(decryptedList.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    tvStreamStatus.setText("Lỗi: " + error);
                    Toast.makeText(ReceiverActivity.this, error, Toast.LENGTH_SHORT).show();
                    if (adapter.getItemCount() == 0) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQ_NOTIF);
            }
        }
    }
}
