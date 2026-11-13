package com.vinabridge.smslite.gateway;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import com.vinabridge.smslite.model.FirebaseSmsRecord;
import com.vinabridge.smslite.model.SmsPayload;
import com.vinabridge.smslite.security.CryptoManager;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

/** Serialized, network-constrained background sync supported from API 21. */
public class GatewaySyncService extends JobService {
    private static final int JOB_ID = 0x564253;
    // Shared with settings/auth integration via Android's default preferences file.
    public static final String DB_URL = "firebase_db_url";
    public static final String UID = "firebase_uid";
    public static final String ID_TOKEN = "firebase_id_token";
    public static final String PASSPHRASE = "encryption_passphrase";
    private volatile Thread worker;
    private volatile HttpsURLConnection connection;

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public static void syncNow(Context context) {
        if (!preferences(context).getBoolean("gateway_enabled", true)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        // Do not replace a running or queued job on every incoming SMS.
        for (JobInfo job : scheduler.getAllPendingJobs()) if (job.getId() == JOB_ID) return;
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, GatewaySyncService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }

    @Override public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            boolean retry = false;
            try (GatewayDbHelper db = new GatewayDbHelper(this)) {
                db.deleteOldMessages();
                while (!Thread.currentThread().isInterrupted()
                        && preferences(this).getBoolean("gateway_enabled", true)) {
                    List<SmsPayload> batch = db.getPendingMessages();
                    if (batch.isEmpty()) break;
                    for (SmsPayload payload : batch) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (!preferences(this).getBoolean("gateway_enabled", true)) return;
                        try {
                            upload(payload);
                            db.markMessageSuccess(payload.getId());
                            preferences(this).edit().putLong("last_sync_timestamp", System.currentTimeMillis()).apply();
                        } catch (Exception ignored) {
                            db.incrementRetry(payload.getId());
                            retry = true;
                            return;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                retry = true;
            } finally {
                if (!Thread.currentThread().isInterrupted()) jobFinished(params, retry);
            }
        }, "gateway-sync");
        worker.start();
        return true;
    }

    private void upload(SmsPayload payload) throws Exception {
        SharedPreferences prefs = preferences(this);
        String base = prefs.getString(DB_URL, "").trim();
        if (!base.contains("://")) base = "https://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        URL root = new URL(base);
        if (!"https".equals(root.getProtocol()) || root.getHost().isEmpty()
                || root.getUserInfo() != null || root.getQuery() != null || root.getRef() != null
                || !(root.getPath().isEmpty() || "/".equals(root.getPath()))) {
            throw new IllegalArgumentException("Invalid HTTPS database root");
        }
        String uid = prefs.getString(UID, "");
        String token = prefs.getString(ID_TOKEN, "");
        if (uid.isEmpty() || token.isEmpty()) throw new IllegalStateException("Authentication required");
        String encrypted = new CryptoManager().encrypt(payload.toJson(), prefs.getString(PASSPHRASE, ""));
        byte[] body = new FirebaseSmsRecord(payload.getId(), encrypted, payload.getTimestamp(),
                prefs.getString("device_id", "gateway")).toJson().getBytes(StandardCharsets.UTF_8);
        URL url = new URL(base + "/users/" + encode(uid) + "/sms/" + encode(payload.getId())
                + ".json?auth=" + encode(token));
        HttpsURLConnection request = (HttpsURLConnection) url.openConnection();
        connection = request;
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            request.setConnectTimeout(15000);
            request.setReadTimeout(15000);
            request.setInstanceFollowRedirects(false);
            request.setRequestMethod("PUT");
            request.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            request.setDoOutput(true);
            request.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = request.getOutputStream()) { out.write(body); }
            if (request.getResponseCode() != 200) throw new java.io.IOException("Upload failed");
        } finally {
            request.disconnect();
            connection = null;
        }
    }

    private static String encode(String value) throws java.io.UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    @Override public boolean onStopJob(JobParameters params) {
        Thread current = worker;
        if (current != null) current.interrupt();
        HttpsURLConnection currentConnection = connection;
        if (currentConnection != null) currentConnection.disconnect();
        return preferences(this).getBoolean("gateway_enabled", true);
    }
}
