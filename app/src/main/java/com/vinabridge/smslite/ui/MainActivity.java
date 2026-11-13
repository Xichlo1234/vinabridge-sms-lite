package com.vinabridge.smslite.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.vinabridge.smslite.R;
import com.vinabridge.smslite.gateway.GatewaySyncService;

public class MainActivity extends AppCompatActivity {
    private TextView tvAuthStatus;
    private TextView tvPairingStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvAuthStatus = findViewById(R.id.tv_auth_status);
        tvPairingStatus = findViewById(R.id.tv_pairing_status);

        Button btnGateway = findViewById(R.id.btn_mode_gateway);
        Button btnReceiver = findViewById(R.id.btn_mode_receiver);
        Button btnSettings = findViewById(R.id.btn_settings_pairing);

        btnGateway.setOnClickListener(v -> startActivity(new Intent(this, GatewayActivity.class)));
        btnReceiver.setOnClickListener(v -> startActivity(new Intent(this, ReceiverActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, PairingActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        SharedPreferences prefs = GatewaySyncService.preferences(this);
        String uid = prefs.getString(GatewaySyncService.UID, "");
        String passphrase = prefs.getString(GatewaySyncService.PASSPHRASE, "");

        if (!uid.isEmpty()) {
            tvAuthStatus.setText("Firebase: Đã đăng nhập (UID: " + uid.substring(0, Math.min(8, uid.length())) + "...)");
            tvAuthStatus.setTextColor(getResources().getColor(R.color.success));
        } else {
            tvAuthStatus.setText("Firebase: Chưa đăng nhập");
            tvAuthStatus.setTextColor(getResources().getColor(R.color.warning));
        }

        if (!passphrase.isEmpty()) {
            tvPairingStatus.setText("Bảo mật: Đã thiết lập khóa mã hóa AES-256");
            tvPairingStatus.setTextColor(getResources().getColor(R.color.success));
        } else {
            tvPairingStatus.setText("Bảo mật: Chưa nhập mã khóa ghép cặp!");
            tvPairingStatus.setTextColor(getResources().getColor(R.color.error));
        }
    }
}
