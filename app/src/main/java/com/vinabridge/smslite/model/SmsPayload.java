package com.vinabridge.smslite.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Data model for the unencrypted SMS message payload.
 */
public class SmsPayload implements Serializable {
    private String id;
    private String sender;
    private String body;
    private long timestamp;
    private int simSlot;

    public SmsPayload() {
    }

    public SmsPayload(String sender, String body, long timestamp, int simSlot) {
        this.sender = sender != null ? sender.trim() : "";
        this.body = body != null ? body : "";
        this.timestamp = timestamp;
        this.simSlot = simSlot;
        this.id = generateMessageId(this.sender, this.body, this.timestamp);
    }

    public SmsPayload(String id, String sender, String body, long timestamp, int simSlot) {
        this.id = id;
        this.sender = sender;
        this.body = body;
        this.timestamp = timestamp;
        this.simSlot = simSlot;
    }

    /**
     * Deterministic message ID generation to avoid duplicate uploads.
     */
    public static String generateMessageId(String sender, String body, long timestamp) {
        try {
            // Round timestamp to nearest 2 seconds to absorb slight reception delta on multipart parts
            long roundedTime = (timestamp / 2000) * 2000;
            String raw = (sender != null ? sender : "") + "|" + (body != null ? body : "") + "|" + roundedTime;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 24); // 24-character unique hash
        } catch (NoSuchAlgorithmException e) {
            return "msg_" + (sender != null ? sender.hashCode() : 0) + "_" + timestamp;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getSimSlot() {
        return simSlot;
    }

    public void setSimSlot(int simSlot) {
        this.simSlot = simSlot;
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("sender", sender);
            obj.put("body", body);
            obj.put("timestamp", timestamp);
            obj.put("simSlot", simSlot);
            return obj.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static SmsPayload fromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            String id = obj.optString("id", "");
            String sender = obj.optString("sender", "");
            String body = obj.optString("body", "");
            long timestamp = obj.optLong("timestamp", 0);
            int simSlot = obj.optInt("simSlot", 0);
            return new SmsPayload(id, sender, body, timestamp, simSlot);
        } catch (JSONException e) {
            return null;
        }
    }
}
