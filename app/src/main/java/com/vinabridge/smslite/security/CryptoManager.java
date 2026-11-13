package com.vinabridge.smslite.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * High-security End-to-End Encryption engine for SMS Payloads.
 * Uses AES-256-GCM with PBKDF2WithHmacSHA256 key derivation.
 *
 * Wire payload format (Base64 encoded):
 * [16 bytes Salt] + [12 bytes IV] + [Ciphertext + 16 bytes GCM Auth Tag]
 *
 * Android Version Compatibility Note:
 * - API 23+ (Android 6.0+): Android Keystore natively supports AES/GCM keys.
 * - API 21-22 (Android 5.0-5.1): Keystore does not support symmetric AES keys directly.
 *   Hence, key derivation via PBKDF2WithHmacSHA256 with 10,000 iterations and per-message
 *   random salt is used universally across all versions to ensure 100% interoperability
 *   between Gateway and Receiver devices.
 */
public class CryptoManager {
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final int SALT_LENGTH = 16;       // 128-bit salt
    private static final int IV_LENGTH = 12;         // 96-bit IV recommended for GCM
    private static final int TAG_LENGTH_BITS = 128;  // 128-bit authentication tag
    private static final int KEY_LENGTH_BITS = 256;  // AES-256
    private static final int PBKDF2_ITERATIONS = 10000;

    private final SecureRandom secureRandom;

    public CryptoManager() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Derives a 256-bit AES SecretKey from a user passphrase and a given salt.
     */
    public SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        if (passphrase == null || passphrase.trim().isEmpty()) {
            throw new IllegalArgumentException("Passphrase cannot be empty");
        }
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGO);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Base64-encoded string containing [Salt (16B) + IV (12B) + Ciphertext + Tag].
     */
    public String encrypt(String plainText, String passphrase) throws Exception {
        if (plainText == null) return null;
        if (passphrase == null || passphrase.trim().isEmpty()) {
            throw new IllegalArgumentException("Passphrase cannot be empty for encryption");
        }

        // 1. Generate random salt & IV
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        // 2. Derive key
        SecretKey key = deriveKey(passphrase, salt);

        // 3. Encrypt with GCM
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] cipherTextWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // 4. Combine into byte array: [Salt][IV][Ciphertext+Tag]
        ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + cipherTextWithTag.length);
        byteBuffer.put(salt);
        byteBuffer.put(iv);
        byteBuffer.put(cipherTextWithTag);

        return encodeBase64(byteBuffer.array());
    }

    /**
     * Decrypts Base64-encoded string containing [Salt (16B) + IV (12B) + Ciphertext + Tag].
     * Returns original plaintext string.
     */
    public String decrypt(String base64Payload, String passphrase) throws Exception {
        if (base64Payload == null || base64Payload.trim().isEmpty()) return null;
        if (passphrase == null || passphrase.trim().isEmpty()) {
            throw new IllegalArgumentException("Passphrase cannot be empty for decryption");
        }

        byte[] combined = decodeBase64(base64Payload.trim());
        if (combined.length < (SALT_LENGTH + IV_LENGTH + 16)) {
            throw new IllegalArgumentException("Invalid payload length: corrupted or tampered ciphertext");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(combined);

        // 1. Extract Salt
        byte[] salt = new byte[SALT_LENGTH];
        byteBuffer.get(salt);

        // 2. Extract IV
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        // 3. Extract Ciphertext + Tag
        byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherTextWithTag);

        // 4. Derive key
        SecretKey key = deriveKey(passphrase, salt);

        // 5. Decrypt with GCM
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] plainBytes = cipher.doFinal(cipherTextWithTag);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // Helper for cross-platform Base64 (Android and JVM Unit test compatibility)
    public static String encodeBase64(byte[] data) {
        try {
            Class<?> base64Class = Class.forName("android.util.Base64");
            java.lang.reflect.Method encodeMethod = base64Class.getMethod("encodeToString", byte[].class, int.class);
            return (String) encodeMethod.invoke(null, data, 2); // 2 = NO_WRAP
        } catch (Exception e) {
            // Standard Java 8 fallback
            return java.util.Base64.getEncoder().withoutPadding().encodeToString(data);
        }
    }

    public static byte[] decodeBase64(String base64Str) {
        try {
            Class<?> base64Class = Class.forName("android.util.Base64");
            java.lang.reflect.Method decodeMethod = base64Class.getMethod("decode", String.class, int.class);
            return (byte[]) decodeMethod.invoke(null, base64Str, 2); // 2 = NO_WRAP
        } catch (Exception e) {
            // Standard Java 8 fallback
            return java.util.Base64.getDecoder().decode(base64Str);
        }
    }
}
