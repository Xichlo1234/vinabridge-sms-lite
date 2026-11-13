package com.vinabridge.smslite.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import com.vinabridge.smslite.gateway.GatewaySyncService;
import com.vinabridge.smslite.model.FirebaseSmsRecord;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

/**
 * Lightweight Firebase Realtime Database REST Client.
 * Supports single snapshot retrieval (GET) and long-lived SSE (Server-Sent Events) streaming.
 */
public class FirebaseDbRestClient {

    public interface SnapshotCallback {
        void onSuccess(List<FirebaseSmsRecord> records);
        void onError(String error);
    }

    public interface StreamListener {
        void onRecordReceived(FirebaseSmsRecord record);
        void onConnected();
        void onDisconnected(String reason);
    }

    /**
     * Normalizes the base database URL.
     */
    public static String getNormalizedDbUrl(Context context) {
        SharedPreferences prefs = GatewaySyncService.preferences(context);
        String base = prefs.getString(GatewaySyncService.DB_URL, "").trim();
        if (base.isEmpty()) return "";
        if (!base.contains("://")) base = "https://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    /**
     * Fetches all current SMS records for the authenticated UID.
     */
    public static void fetchAllRecords(Context context, SnapshotCallback callback) {
        new Thread(() -> {
            HttpsURLConnection conn = null;
            try {
                String dbUrl = getNormalizedDbUrl(context);
                String uid = GatewaySyncService.preferences(context).getString(GatewaySyncService.UID, "");
                String idToken = FirebaseAuthRestClient.getValidIdToken(context);

                if (dbUrl.isEmpty() || uid.isEmpty() || idToken == null || idToken.isEmpty()) {
                    if (callback != null) callback.onError("Chưa đăng nhập hoặc chưa cấu hình URL Firebase");
                    return;
                }

                String urlStr = dbUrl + "/users/" + URLEncoder.encode(uid, "UTF-8") + "/sms.json?auth=" + URLEncoder.encode(idToken, "UTF-8");
                URL url = new URL(urlStr);
                conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String jsonText = readStream(conn.getInputStream());
                    List<FirebaseSmsRecord> list = new ArrayList<>();
                    if (jsonText != null && !jsonText.trim().equals("null") && !jsonText.trim().isEmpty()) {
                        JSONObject rootObj = new JSONObject(jsonText);
                        Iterator<String> keys = rootObj.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            JSONObject item = rootObj.getJSONObject(key);
                            FirebaseSmsRecord record = FirebaseSmsRecord.fromJson(item.toString());
                            if (record != null) {
                                if (record.getId() == null || record.getId().isEmpty()) {
                                    record.setId(key);
                                }
                                list.add(record);
                            }
                        }
                    }
                    if (callback != null) callback.onSuccess(list);
                } else {
                    if (callback != null) callback.onError("Lỗi tải dữ liệu: HTTP " + responseCode);
                }
            } catch (Exception e) {
                if (callback != null) callback.onError("Lỗi kết nối: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "firebase-db-get").start();
    }

    /**
     * Opens a long-lived Server-Sent Events (SSE) stream to listen for real-time updates.
     * Blocks the calling thread; intended to run inside a worker thread or Service.
     */
    public static void listenStreamBlocking(Context context, StreamListener listener, AtomicBoolean stopSignal) {
        HttpsURLConnection conn = null;
        try {
            String dbUrl = getNormalizedDbUrl(context);
            String uid = GatewaySyncService.preferences(context).getString(GatewaySyncService.UID, "");
            String idToken = FirebaseAuthRestClient.getValidIdToken(context);

            if (dbUrl.isEmpty() || uid.isEmpty() || idToken == null) {
                if (listener != null) listener.onDisconnected("Thiếu thông tin xác thực Firebase");
                return;
            }

            String urlStr = dbUrl + "/users/" + URLEncoder.encode(uid, "UTF-8") + "/sms.json?auth=" + URLEncoder.encode(idToken, "UTF-8");
            URL url = new URL(urlStr);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000); // 60s read timeout for keep-alive

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                if (listener != null) listener.onConnected();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    String currentEvent = "";
                    while (!stopSignal.get() && (line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("event:")) {
                            currentEvent = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (!data.equals("null") && !data.isEmpty()) {
                                handleStreamData(currentEvent, data, listener);
                            }
                        }
                    }
                }
            } else {
                if (listener != null) listener.onDisconnected("HTTP error: " + responseCode);
            }
        } catch (Exception e) {
            if (listener != null) listener.onDisconnected(e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void handleStreamData(String event, String data, StreamListener listener) {
        if (listener == null) return;
        try {
            JSONObject json = new JSONObject(data);
            String path = json.optString("path", "/");
            Object dataObj = json.opt("data");

            if (dataObj instanceof JSONObject) {
                JSONObject payloadObj = (JSONObject) dataObj;
                if (path.equals("/")) {
                    // Initial snapshot: iterate children
                    Iterator<String> keys = payloadObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        JSONObject child = payloadObj.optJSONObject(key);
                        if (child != null) {
                            FirebaseSmsRecord rec = FirebaseSmsRecord.fromJson(child.toString());
                            if (rec != null) {
                                if (rec.getId() == null || rec.getId().isEmpty()) rec.setId(key);
                                listener.onRecordReceived(rec);
                            }
                        }
                    }
                } else {
                    // Single item updated/inserted
                    FirebaseSmsRecord rec = FirebaseSmsRecord.fromJson(payloadObj.toString());
                    if (rec != null) {
                        listener.onRecordReceived(rec);
                    }
                }
            }
        } catch (Exception ignored) {
        }
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

    public static class AtomicBoolean {
        private volatile boolean value;
        public AtomicBoolean(boolean initial) { this.value = initial; }
        public boolean get() { return value; }
        public void set(boolean v) { this.value = v; }
    }
}
