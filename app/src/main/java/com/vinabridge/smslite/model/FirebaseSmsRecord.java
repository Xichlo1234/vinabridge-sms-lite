package com.vinabridge.smslite.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Data record matching Firebase Realtime Database schema:
 * users/{uid}/sms/{messageId}
 */
public class FirebaseSmsRecord implements Serializable {
    public static final int CURRENT_VERSION = 1;

    private String id;
    private String encryptedPayload;
    private long timestamp;
    private String deviceId;
    private int version;

    public FirebaseSmsRecord() {
        this.version = CURRENT_VERSION;
    }

    public FirebaseSmsRecord(String id, String encryptedPayload, long timestamp, String deviceId) {
        this.id = id;
        this.encryptedPayload = encryptedPayload;
        this.timestamp = timestamp;
        this.deviceId = deviceId;
        this.version = CURRENT_VERSION;
    }

    public FirebaseSmsRecord(String id, String encryptedPayload, long timestamp, String deviceId, int version) {
        this.id = id;
        this.encryptedPayload = encryptedPayload;
        this.timestamp = timestamp;
        this.deviceId = deviceId;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public void setEncryptedPayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("encryptedPayload", encryptedPayload);
            obj.put("timestamp", timestamp);
            obj.put("deviceId", deviceId);
            obj.put("version", version);
            return obj.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static FirebaseSmsRecord fromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            String id = obj.optString("id", "");
            String encryptedPayload = obj.optString("encryptedPayload", "");
            long timestamp = obj.optLong("timestamp", 0);
            String deviceId = obj.optString("deviceId", "");
            int version = obj.optInt("version", CURRENT_VERSION);
            return new FirebaseSmsRecord(id, encryptedPayload, timestamp, deviceId, version);
        } catch (JSONException e) {
            return null;
        }
    }
}
