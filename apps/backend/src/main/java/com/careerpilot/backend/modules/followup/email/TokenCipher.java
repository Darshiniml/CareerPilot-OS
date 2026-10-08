package com.careerpilot.backend.modules.followup.email;

import com.careerpilot.backend.config.CodedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts OAuth refresh tokens at rest (AES-256-GCM) and signs OAuth state values (HMAC-SHA256).
 * The 32-byte key comes from {@code EMAIL_TOKEN_ENCRYPTION_KEY} (base64); without it, mailbox
 * connections are disabled rather than stored in plaintext.
 */
@Component
public class TokenCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public TokenCipher(@Value("${careerpilot.email.token-encryption-key:}") String base64Key) {
        byte[] decoded = null;
        if (base64Key != null && !base64Key.isBlank()) {
            decoded = Base64.getDecoder().decode(base64Key.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("EMAIL_TOKEN_ENCRYPTION_KEY must be 32 bytes (base64-encoded)");
            }
        }
        this.key = decoded;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Stored token could not be decrypted (key changed or data corrupted)", e);
        }
    }

    public String sign(String value) {
        requireKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    public boolean verify(String value, String signature) {
        return signature != null && MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private void requireKey() {
        if (key == null) {
            throw new CodedException("Email connections are disabled: EMAIL_TOKEN_ENCRYPTION_KEY is not configured",
                    "EMAIL_NOT_CONFIGURED", 503, null);
        }
    }
}
