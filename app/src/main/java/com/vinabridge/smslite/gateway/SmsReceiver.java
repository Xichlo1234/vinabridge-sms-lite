package com.vinabridge.smslite.gateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.vinabridge.smslite.model.SmsPayload;

public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())
                || !GatewaySyncService.preferences(context).getBoolean("gateway_enabled", true)) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try (GatewayDbHelper db = new GatewayDbHelper(app)) {
                SmsPayload payload = SmsParser.parseFromIntent(intent);
                if (payload != null && SmsParser.markIfNotDuplicate(payload.getId())) {
                    try {
                        db.insertMessage(payload);
                    } catch (RuntimeException failure) {
                        SmsParser.forgetMessage(payload.getId());
                        throw failure;
                    }
                    GatewaySyncService.syncNow(app);
                }
            } catch (RuntimeException ignored) {
                // Never log exceptions containing SMS data or database bind arguments.
            } finally {
                pending.finish();
            }
        }, "gateway-sms").start();
    }
}
