package com.vinabridge.smslite;

import com.vinabridge.smslite.model.SmsPayload;
import org.junit.Assert;
import org.junit.Test;

public class SmsPayloadTest {

    @Test
    public void testDeterministicMessageId() {
        String sender = "VinaPhone";
        String body = "Nap tien thanh cong 50000d";
        long timestamp = 1700000000000L;

        String id1 = SmsPayload.generateMessageId(sender, body, timestamp);
        String id2 = SmsPayload.generateMessageId(sender, body, timestamp + 500); // within 2s window

        Assert.assertEquals("ID should match within 2-second timestamp window", id1, id2);
    }

    @Test
    public void testJsonSerialization() {
        SmsPayload payload = new SmsPayload("VNPT-OTP", "Mã xác thực của bạn là: 654321", 1700000005000L, 0);
        String json = payload.toJson();

        SmsPayload parsed = SmsPayload.fromJson(json);
        Assert.assertNotNull(parsed);
        Assert.assertEquals(payload.getId(), parsed.getId());
        Assert.assertEquals(payload.getSender(), parsed.getSender());
        Assert.assertEquals(payload.getBody(), parsed.getBody());
        Assert.assertEquals(payload.getTimestamp(), parsed.getTimestamp());
        Assert.assertEquals(payload.getSimSlot(), parsed.getSimSlot());
    }
}
