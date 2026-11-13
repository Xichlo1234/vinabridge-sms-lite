package com.vinabridge.smslite.gateway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.vinabridge.smslite.model.SmsPayload;
import java.util.ArrayList;
import java.util.List;

/** Durable FIFO queue. Successful IDs are retained for seven days to suppress redelivery. */
public class GatewayDbHelper extends SQLiteOpenHelper {
    public GatewayDbHelper(Context context) {
        super(context.getApplicationContext(), "gateway.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending_sms (id TEXT PRIMARY KEY NOT NULL, sender TEXT, "
                + "body TEXT, timestamp INTEGER, sim_slot INTEGER, retry_count INTEGER NOT NULL DEFAULT 0, "
                + "status TEXT NOT NULL DEFAULT 'pending', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX queue_order ON pending_sms(status, created_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported gateway database upgrade");
    }

    public boolean insertMessage(SmsPayload message) {
        if (message == null || message.getId() == null || message.getId().isEmpty()) {
            throw new IllegalArgumentException("Message ID required");
        }
        ContentValues values = new ContentValues();
        values.put("id", message.getId());
        values.put("sender", message.getSender());
        values.put("body", message.getBody());
        values.put("timestamp", message.getTimestamp());
        values.put("sim_slot", message.getSimSlot());
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict("pending_sms", null, values,
                SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public List<SmsPayload> getPendingMessages() {
        List<SmsPayload> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("pending_sms",
                new String[]{"id", "sender", "body", "timestamp", "sim_slot"},
                "status = ?", new String[]{"pending"}, null, null, "created_at ASC, rowid ASC", "100")) {
            while (cursor.moveToNext()) {
                result.add(new SmsPayload(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getLong(3), cursor.getInt(4)));
            }
        }
        return result;
    }

    public void markMessageSuccess(String id) {
        ContentValues values = new ContentValues();
        values.put("status", "success");
        values.putNull("sender");
        values.putNull("body");
        getWritableDatabase().update("pending_sms", values, "id = ?", new String[]{id});
    }

    public void incrementRetry(String id) {
        getWritableDatabase().execSQL("UPDATE pending_sms SET retry_count = retry_count + 1 "
                + "WHERE id = ? AND status = 'pending'", new Object[]{id});
    }

    public int getPendingCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM pending_sms WHERE status = 'pending'", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    /** Never expire unsent SMS, regardless of age or retry count. */
    public void deleteOldMessages() {
        getWritableDatabase().delete("pending_sms", "status = ? AND created_at < ?",
                new String[]{"success", Long.toString(System.currentTimeMillis() - 7L * 86400000)});
    }
}
