package com.perpus.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordUtil {
    private PasswordUtil() {}

    public static String hash(String password) {
        if (password == null) throw new IllegalArgumentException("Password tidak boleh null");
        if (password.isBlank()) throw new IllegalArgumentException("Password tidak boleh kosong");
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 120000, 256))
                    .getEncoded();
            return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal mengenkripsi password", e);
        }
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        if (isHashed(stored)) {
            try {
                String[] parts = stored.split("\\$");
                if (parts.length != 3) return false;
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 120000, 256))
                        .getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } catch (Exception e) {
                return false;
            }
        }
        return stored.equals(password);
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith("pbkdf2$");
    }
}