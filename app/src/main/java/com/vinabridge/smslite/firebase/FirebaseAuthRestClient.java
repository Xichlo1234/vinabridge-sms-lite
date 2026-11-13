package com.vinabridge.smslite.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import com.vinabridge.smslite.gateway.GatewaySyncService;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * Lightweight Firebase Authentication Client using Firebase REST API.
 * Requires ZERO Firebase Android SDK libraries, compatible from Android 5.0 (API 21).
 */
public class FirebaseAuthRestClient {
    private static final String SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private static final String REFRESH_TOKEN_URL = "https://securetoken.googleapis.com/v1/token?key=";

    public static final String PREF_API_KEY = "firebase_api_key";
    public static final String PREF_REFRESH_TOKEN = "firebase_refresh_token";
    public static final String PREF_TOKEN_EXPIRY = "firebase_token_expiry";

    public interface AuthCallback {
        void onSuccess(String uid, String idToken);
        void onError(String errorMessage);
    }

    /**
     * Signs in with email and password via Firebase Auth REST API.
     */
    public static void signIn(Context context, String apiKey, String email, String password, AuthCallback callback) {
        new Thread(() -> {
            HttpsURLConnection conn = null;
            try {
                URL url = new URL(SIGN_IN_URL + apiKey.trim());
                conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("email", email.trim());
                payload.put("password", password);
                payload.put("returnSecureToken", true);

                byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseText = readStream(is);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONObject json = new JSONObject(responseText);
                    String idToken = json.getString("idToken");
                    String refreshToken = json.getString("refreshToken");
                    String uid = json.getString("localId");
                    long expiresIn = json.optLong("expiresIn", 3600);
                    long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);

                    // Save credentials in SharedPreferences
                    SharedPreferences prefs = GatewaySyncService.preferences(context);
                    prefs.edit()
                            .putString(PREF_API_KEY, apiKey.trim())
                            .putString(GatewaySyncService.ID_TOKEN, idToken)
                            .putString(PREF_REFRESH_TOKEN, refreshToken)
                            .putString(GatewaySyncService.UID, uid)
                            .putLong(PREF_TOKEN_EXPIRY, expiryTime)
                            .apply();

                    if (callback != null) callback.onSuccess(uid, idToken);
                } else {
                    String errorMsg = "Đăng nhập thất bại (" + responseCode + ")";
                    try {
                        JSONObject errJson = new JSONObject(responseText);
                        if (errJson.has("error") && errJson.getJSONObject("error").has("message")) {
                            errorMsg += ": " + errJson.getJSONObject("error").getString("message");
                        }
                    } catch (Exception ignored) {}
                    if (callback != null) callback.onError(errorMsg);
                }
            } catch (Exception e) {
                if (callback != null) callback.onError("Lỗi kết nối mạng: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "firebase-auth").start();
    }

    /**
     * Retrieves valid idToken, automatically refreshing if expired or expiring within 5 minutes.
     */
    public static String getValidIdToken(Context context) {
        SharedPreferences prefs = GatewaySyncService.preferences(context);
        String idToken = prefs.getString(GatewaySyncService.ID_TOKEN, "");
        String refreshToken = prefs.getString(PREF_REFRESH_TOKEN, "");
        String apiKey = prefs.getString(PREF_API_KEY, "");
        long expiryTime = prefs.getLong(PREF_TOKEN_EXPIRY, 0);

        if (idToken.isEmpty() || apiKey.isEmpty()) {
            return null;
        }

        // If token expires within 5 minutes, refresh it
        if (System.currentTimeMillis() + (300 * 1000) > expiryTime) {
            if (refreshToken.isEmpty()) return null;
            return refreshIdToken(context, apiKey, refreshToken);
        }

        return idToken;
    }

    private static String refreshIdToken(Context context, String apiKey, String refreshToken) {
        HttpsURLConnection conn = null;
        try {
            URL url = new URL(REFRESH_TOKEN_URL + apiKey.trim());
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            String urlParameters = "grant_type=refresh_token&refresh_token=" + refreshToken;
            try (OutputStream os = conn.getOutputStream()) {
                os.write(urlParameters.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseText = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(responseText);
                String newIdToken = json.getString("id_token");
                String newRefreshToken = json.optString("refresh_token", refreshToken);
                long expiresIn = json.optLong("expires_in", 3600);
                long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);

                GatewaySyncService.preferences(context).edit()
                        .putString(GatewaySyncService.ID_TOKEN, newIdToken)
                        .putString(PREF_REFRESH_TOKEN, newRefreshToken)
                        .putLong(PREF_TOKEN_EXPIRY, expiryTime)
                        .apply();

                return newIdToken;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
