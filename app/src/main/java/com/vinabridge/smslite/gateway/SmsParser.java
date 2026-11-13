package com.vinabridge.smslite.gateway;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import com.vinabridge.smslite.model.SmsPayload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Robust parser for incoming SMS PDUs with multipart SMS concatenation
 * and in-memory deduplication.
 */
public class SmsParser {
    // Deduplication window: keep recent message IDs to prevent double broadcast processing
    private static final int MAX_CACHE_SIZE = 100;
    private static final Map<String, Long> PROCESSED_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    /**
     * Parses standard SMS PDUs from Intent and stitches multipart messages into one SmsPayload.
     */
    public static SmsPayload parseFromIntent(Intent intent) {
        if (intent == null) return null;
        Bundle bundle = intent.getExtras();
        if (bundle == null) return null;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) return null;

        String format = bundle.getString("format");
        StringBuilder bodyBuilder = new StringBuilder();
        String sender = "";
        long timestamp = System.currentTimeMillis();

        for (Object pdu : pdus) {
            SmsMessage smsMessage;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
            }

            if (smsMessage != null) {
                if (sender.isEmpty()) {
                    sender = smsMessage.getDisplayOriginatingAddress();
                    if (sender == null || sender.isEmpty()) {
                        sender = smsMessage.getOriginatingAddress();
                    }
                }
                long msgTime = smsMessage.getTimestampMillis();
                if (msgTime > 0) {
                    timestamp = msgTime;
                }
                String msgBody = smsMessage.getDisplayMessageBody();
                if (msgBody == null) {
                    msgBody = smsMessage.getMessageBody();
                }
                if (msgBody != null) {
                    bodyBuilder.append(msgBody);
                }
            }
        }

        int simSlot = bundle.getInt("slot", bundle.getInt("simSlot", bundle.getInt("subscription", 0)));
        String fullBody = bodyBuilder.toString();

        return new SmsPayload(sender, fullBody, timestamp, simSlot);
    }

    /**
     * Checks if this message has already been processed within the dedup window.
     * Returns true if newly added (not duplicate), false if already seen.
     */
    public static synchronized boolean markIfNotDuplicate(String messageId) {
        if (messageId == null || messageId.isEmpty()) return false;
        long now = System.currentTimeMillis();
        Long seenAt = PROCESSED_CACHE.get(messageId);
        if (seenAt != null && (now - seenAt) < 60000) {
            // Already seen in the last 60 seconds
            return false;
        }
        PROCESSED_CACHE.put(messageId, now);
        return true;
    }

    public static void clearCache() {
        PROCESSED_CACHE.clear();
    }

    /** Release a reservation when persisting the message failed. */
    public static synchronized void forgetMessage(String messageId) {
        PROCESSED_CACHE.remove(messageId);
    }
}
