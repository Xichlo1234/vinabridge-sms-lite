package com.vinabridge.smslite.ui;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.vinabridge.smslite.R;
import com.vinabridge.smslite.firebase.FirebaseAuthRestClient;
import com.vinabridge.smslite.gateway.GatewaySyncService;

public class PairingActivity extends AppCompatActivity {
    private EditText etDbUrl;
    private EditText etApiKey;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etPassphrase;
    private TextView tvLoginStatus;
    private TextView tvDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);

        etDbUrl = findViewById(R.id.et_db_url);
        etApiKey = findViewById(R.id.et_api_key);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etPassphrase = findViewById(R.id.et_passphrase);
        tvLoginStatus = findViewById(R.id.tv_login_status);
        tvDeviceId = findViewById(R.id.tv_device_id);

        Button btnLogin = findViewById(R.id.btn_login_firebase);
        Button btnSave = findViewById(R.id.btn_save_security);
        Button btnLogout = findViewById(R.id.btn_logout);

        loadSavedSettings();

        btnLogin.setOnClickListener(v -> doFirebaseLogin());
        btnSave.setOnClickListener(v -> saveSecuritySettings());
        btnLogout.setOnClickListener(v -> doLogout());
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = GatewaySyncService.preferences(this);

        String dbUrl = prefs.getString(GatewaySyncService.DB_URL, "");
        String apiKey = prefs.getString(FirebaseAuthRestClient.PREF_API_KEY, "");
        String passphrase = prefs.getString(GatewaySyncService.PASSPHRASE, "");
        String uid = prefs.getString(GatewaySyncService.UID, "");

        etDbUrl.setText(dbUrl);
        etApiKey.setText(apiKey);
        etPassphrase.setText(passphrase);

        if (!uid.isEmpty()) {
            tvLoginStatus.setText("Đã đăng nhập: UID " + uid);
            tvLoginStatus.setTextColor(getResources().getColor(R.color.success));
        } else {
            tvLoginStatus.setText("Chưa đăng nhập");
            tvLoginStatus.setTextColor(getResources().getColor(R.color.warning));
        }

        String deviceId = getUniqueDeviceId();
        tvDeviceId.setText(getString(R.string.device_id_label) + " " + deviceId);
        prefs.edit().putString("device_id", deviceId).apply();
    }

    private String getUniqueDeviceId() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = Build.SERIAL != null ? Build.SERIAL : "device_" + System.currentTimeMillis();
        }
        return Build.MANUFACTURER + "_" + Build.MODEL + "_" + androidId.substring(0, Math.min(6, androidId.length()));
    }

    private void doFirebaseLogin() {
        String apiKey = etApiKey.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String dbUrl = etDbUrl.getText().toString().trim();

        if (apiKey.isEmpty() || email.isEmpty() || password.isEmpty() || dbUrl.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ DB URL, API Key, Email và Mật khẩu", Toast.LENGTH_LONG).show();
            return;
        }

        GatewaySyncService.preferences(this).edit().putString(GatewaySyncService.DB_URL, dbUrl).apply();

        tvLoginStatus.setText("Đang đăng nhập...");
        FirebaseAuthRestClient.signIn(this, apiKey, email, password, new FirebaseAuthRestClient.AuthCallback() {
            @Override
            public void onSuccess(String uid, String idToken) {
                runOnUiThread(() -> {
                    tvLoginStatus.setText("Đăng nhập thành công! UID: " + uid);
                    tvLoginStatus.setTextColor(getResources().getColor(R.color.success));
                    Toast.makeText(PairingActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    tvLoginStatus.setText("Lỗi: " + errorMessage);
                    tvLoginStatus.setTextColor(getResources().getColor(R.color.error));
                    Toast.makeText(PairingActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveSecuritySettings() {
        String passphrase = etPassphrase.getText().toString().trim();
        String dbUrl = etDbUrl.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();

        if (passphrase.isEmpty()) {
            Toast.makeText(this, "Mã khóa bảo mật (Passphrase) không được để trống!", Toast.LENGTH_SHORT).show();
            return;
        }

        GatewaySyncService.preferences(this).edit()
                .putString(GatewaySyncService.PASSPHRASE, passphrase)
                .putString(GatewaySyncService.DB_URL, dbUrl)
                .putString(FirebaseAuthRestClient.PREF_API_KEY, apiKey)
                .apply();

        Toast.makeText(this, "Đã lưu cài đặt bảo mật và ghép cặp thành công!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void doLogout() {
        GatewaySyncService.preferences(this).edit()
                .remove(GatewaySyncService.UID)
                .remove(GatewaySyncService.ID_TOKEN)
                .remove(FirebaseAuthRestClient.PREF_REFRESH_TOKEN)
                .remove(FirebaseAuthRestClient.PREF_TOKEN_EXPIRY)
                .apply();

        tvLoginStatus.setText("Đã đăng xuất");
        tvLoginStatus.setTextColor(getResources().getColor(R.color.warning));
        Toast.makeText(this, "Đã đăng xuất tài khoản Firebase!", Toast.LENGTH_SHORT).show();
    }
}
