package com.vinabridge.smslite.ui;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.vinabridge.smslite.R;
import com.vinabridge.smslite.gateway.GatewayDbHelper;
import com.vinabridge.smslite.gateway.GatewaySyncService;
import com.vinabridge.smslite.model.SmsPayload;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GatewayActivity extends AppCompatActivity {
    private static final int PERMISSION_REQ_SMS = 101;

    private SwitchCompat switchGateway;
    private TextView tvConnectionStatus;
    private TextView tvLastSync;
    private TextView tvQueueCount;
    private TextView tvPermWarning;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gateway);

        switchGateway = findViewById(R.id.switch_gateway);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvLastSync = findViewById(R.id.tv_last_sync);
        tvQueueCount = findViewById(R.id.tv_queue_count);
        tvPermWarning = findViewById(R.id.tv_perm_warning);

        Button btnTestSms = findViewById(R.id.btn_test_sms);
        Button btnSyncNow = findViewById(R.id.btn_sync_now);

        SharedPreferences prefs = GatewaySyncService.preferences(this);
        boolean isEnabled = prefs.getBoolean("gateway_enabled", true);
        switchGateway.setChecked(isEnabled);

        switchGateway.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("gateway_enabled", isChecked).apply();
            if (isChecked) {
                GatewaySyncService.syncNow(this);
            }
            updateStatusDisplay();
        });

        btnTestSms.setOnClickListener(v -> sendTestSms());
        btnSyncNow.setOnClickListener(v -> {
            GatewaySyncService.syncNow(this);
            Toast.makeText(this, "Đã kích hoạt đồng bộ hàng đợi!", Toast.LENGTH_SHORT).show();
            updateStatusDisplay();
        });

        tvPermWarning.setOnClickListener(v -> checkAndRequestSmsPermission());

        checkAndRequestSmsPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusDisplay();
    }

    private void updateStatusDisplay() {
        SharedPreferences prefs = GatewaySyncService.preferences(this);
        String uid = prefs.getString(GatewaySyncService.UID, "");
        String dbUrl = prefs.getString(GatewaySyncService.DB_URL, "");

        if (!uid.isEmpty() && !dbUrl.isEmpty()) {
            tvConnectionStatus.setText("Firebase: Đã cấu hình và sẵn sàng");
            tvConnectionStatus.setTextColor(getResources().getColor(R.color.success));
        } else {
            tvConnectionStatus.setText("Firebase: Chưa cấu hình đầy đủ");
            tvConnectionStatus.setTextColor(getResources().getColor(R.color.warning));
        }

        long lastSync = prefs.getLong("last_sync_timestamp", 0);
        if (lastSync > 0) {
            tvLastSync.setText(getString(R.string.last_sync_prefix, dateFormat.format(new Date(lastSync))));
        } else {
            tvLastSync.setText(getString(R.string.last_sync_never));
        }

        try (GatewayDbHelper db = new GatewayDbHelper(this)) {
            int count = db.getPendingCount();
            tvQueueCount.setText(getString(R.string.gateway_queue_status, count));
        } catch (Exception e) {
            tvQueueCount.setText("Hàng đợi chưa gửi: 0 tin");
        }
    }

    private void sendTestSms() {
        String testSender = "VinaPhone-TEST";
        String testBody = "Test OTP: 654321. Thử nghiệm chuyển tiếp VinaBridge SMS Lite lúc "
                + dateFormat.format(new Date());
        long now = System.currentTimeMillis();

        SmsPayload testPayload = new SmsPayload(testSender, testBody, now, 0);

        try (GatewayDbHelper db = new GatewayDbHelper(this)) {
            db.insertMessage(testPayload);
            GatewaySyncService.syncNow(this);
            Toast.makeText(this, "Đã thêm tin nhắn thử nghiệm vào hàng đợi và kích hoạt đồng bộ!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi tạo tin thử nghiệm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        updateStatusDisplay();
    }

    private void checkAndRequestSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                tvPermWarning.setVisibility(View.VISIBLE);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS}, PERMISSION_REQ_SMS);
            } else {
                tvPermWarning.setVisibility(View.GONE);
            }
        } else {
            tvPermWarning.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvPermWarning.setVisibility(View.GONE);
                Toast.makeText(this, "Đã cấp quyền nhận SMS thành công!", Toast.LENGTH_SHORT).show();
            } else {
                tvPermWarning.setVisibility(View.VISIBLE);
            }
        }
    }
}
