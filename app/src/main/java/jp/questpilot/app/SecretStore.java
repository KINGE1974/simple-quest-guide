package jp.questpilot.app;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores credentials encrypted with a non-exportable Android Keystore AES key (API 26+). */
final class SecretStore {
    private static final String ALIAS = "jp.questpilot.app.screenscraper_credentials_v1";
    private static final String PREFIX = "cover.secret.";
    private final SharedPreferences prefs;

    SecretStore(SharedPreferences prefs) { this.prefs = prefs; }

    private static SecretKey key() throws GeneralSecurityException {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            java.security.Key existing = store.getKey(ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Keystore unavailable", e);
        }
    }

    String get(String field) {
        String encoded = prefs.getString(PREFIX + field, "");
        if (encoded.isEmpty()) return "";
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
            if (bytes.length < 13) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                    Arrays.copyOfRange(bytes, 0, 12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Restored preferences cannot decrypt without the old device's Keystore key.
            // Fail closed and let the user enter the password again.
            return "";
        }
    }

    void put(String field, String value) throws GeneralSecurityException {
        if (value.isEmpty()) {
            prefs.edit().remove(PREFIX + field).apply();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        prefs.edit().putString(PREFIX + field, Base64.encodeToString(combined, Base64.NO_WRAP)).apply();
    }

    /** Remove old app credentials saved by 0.2.11–0.2.13 without affecting member login. */
    void removeLegacyDeveloperCredentials() {
        prefs.edit().remove("cover.devId").remove("cover.devPassword")
                .remove(PREFIX + "devPassword").apply();
    }
}
