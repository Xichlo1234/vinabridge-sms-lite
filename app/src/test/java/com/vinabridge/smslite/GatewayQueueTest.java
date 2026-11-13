package com.vinabridge.smslite;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import com.vinabridge.smslite.gateway.GatewayDbHelper;
import com.vinabridge.smslite.gateway.SmsParser;
import com.vinabridge.smslite.model.SmsPayload;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, manifest = Config.NONE)
public class GatewayQueueTest {
    private Context context;
    private GatewayDbHelper db;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("gateway.db");
        db = new GatewayDbHelper(context);
        SmsParser.clearCache();
    }

    @After public void tearDown() {
        db.close();
        context.deleteDatabase("gateway.db");
        SmsParser.clearCache();
    }

    private SmsPayload message(long time) {
        return new SmsPayload("Test sender", "Synthetic test message", time, 1);
    }

    @Test public void queuePersistsInArrivalOrderAndPreservesAllFields() {
        SmsPayload first = message(10000);
        SmsPayload second = message(2000);
        assertTrue(db.insertMessage(first));
        assertTrue(db.insertMessage(second));
        db.close();
        db = new GatewayDbHelper(context);
        assertEquals(2, db.getPendingCount());
        SmsPayload loaded = db.getPendingMessages().get(0);
        assertEquals(first.toJson(), loaded.toJson());
        assertEquals(second.getId(), db.getPendingMessages().get(1).getId());
    }

    @Test public void duplicatesDoNotOverwritePendingOrAcknowledgedMessages() {
        SmsPayload payload = message(10000);
        assertTrue(SmsParser.markIfNotDuplicate(payload.getId()));
        assertFalse(SmsParser.markIfNotDuplicate(payload.getId()));
        assertTrue(db.insertMessage(payload));
        assertFalse(db.insertMessage(payload));
        db.markMessageSuccess(payload.getId());
        SmsParser.clearCache();
        assertFalse(db.insertMessage(payload));
        assertEquals(0, db.getPendingCount());
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT sender, body, status FROM pending_sms WHERE id = ?", new String[]{payload.getId()})) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
            assertTrue(cursor.isNull(1));
            assertEquals("success", cursor.getString(2));
        }
    }

    @Test public void retryRetainsPayloadAndIncrementsOnlyPendingRows() {
        SmsPayload payload = message(10000);
        db.insertMessage(payload);
        db.incrementRetry(payload.getId());
        db.incrementRetry(payload.getId());
        assertEquals(payload.toJson(), db.getPendingMessages().get(0).toJson());
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT retry_count FROM pending_sms WHERE id = ?", new String[]{payload.getId()})) {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getInt(0));
        }
        db.markMessageSuccess(payload.getId());
        db.incrementRetry(payload.getId());
        assertTrue(db.getPendingMessages().isEmpty());
    }

    @Test public void cleanupNeverDeletesUnsentMessages() {
        SmsPayload pending = message(10000);
        SmsPayload success = message(20000);
        db.insertMessage(pending);
        db.insertMessage(success);
        db.markMessageSuccess(success.getId());
        ContentValues values = new ContentValues();
        values.put("created_at", 0L);
        db.getWritableDatabase().update("pending_sms", values, null, null);
        db.deleteOldMessages();
        assertEquals(1, db.getPendingCount());
        assertEquals(pending.getId(), db.getPendingMessages().get(0).getId());
        assertTrue(db.insertMessage(success));
    }
}
