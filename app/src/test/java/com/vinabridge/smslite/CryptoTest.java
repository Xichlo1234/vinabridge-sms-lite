package com.vinabridge.smslite;

import com.vinabridge.smslite.security.CryptoManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CryptoTest {
    private CryptoManager cryptoManager;
    private final String PASSPHRASE = "MySecureVinaBridgeKey@2026";

    @Before
    public void setUp() {
        cryptoManager = new CryptoManager();
    }

    @Test
    public void testEncryptionAndDecryptionVietnameseText() throws Exception {
        String originalSms = "Mã OTP VinaPhone của bạn là: 894215. Quý khách tuyệt đối không chia sẻ mã này cho bất kỳ ai!";
        String encrypted = cryptoManager.encrypt(originalSms, PASSPHRASE);

        Assert.assertNotNull(encrypted);
        Assert.assertNotEquals(originalSms, encrypted);

        String decrypted = cryptoManager.decrypt(encrypted, PASSPHRASE);
        Assert.assertEquals(originalSms, decrypted);
    }

    @Test
    public void testRandomIvProducesDifferentOutputs() throws Exception {
        String msg = "OTP: 123456";
        String enc1 = cryptoManager.encrypt(msg, PASSPHRASE);
        String enc2 = cryptoManager.encrypt(msg, PASSPHRASE);

        Assert.assertNotEquals(enc1, enc2);
        Assert.assertEquals(msg, cryptoManager.decrypt(enc1, PASSPHRASE));
        Assert.assertEquals(msg, cryptoManager.decrypt(enc2, PASSPHRASE));
    }

    @Test(expected = Exception.class)
    public void testWrongPassphraseFailsDecryption() throws Exception {
        String original = "Confidential Banking SMS";
        String encrypted = cryptoManager.encrypt(original, PASSPHRASE);
        cryptoManager.decrypt(encrypted, "WrongPassword123");
    }

    @Test(expected = Exception.class)
    public void testTamperedCiphertextThrowsException() throws Exception {
        String original = "Confidential Banking SMS";
        String encrypted = cryptoManager.encrypt(original, PASSPHRASE);

        // Tamper with one character
        char lastChar = encrypted.charAt(encrypted.length() - 1);
        char replacedChar = (lastChar == 'A') ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + replacedChar;

        cryptoManager.decrypt(tampered, PASSPHRASE);
    }

    @Test
    public void testPbkdf2FallbackExactMatch() throws Exception {
        byte[] salt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] standardKey = cryptoManager.deriveKey(PASSPHRASE, salt).getEncoded();
        byte[] fallbackKey = CryptoManager.pbkdf2HmacSha256(
                PASSPHRASE.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                salt,
                10000,
                32
        );

        Assert.assertArrayEquals("Fallback PBKDF2 must match standard JRE PBKDF2 byte-for-byte", standardKey, fallbackKey);
    }
}
