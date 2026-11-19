package ca.mlkem.amazon;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted, app-private storage, explicitly outside Android's backup directory. */
final class ProfileStore {
    private static final String KEY_ALIAS = "aeroplan_helper_profile_v1";
    private static final String FILE_NAME = "aeroplan-helper-profile-v1.bin";

    static final class Profile {
        final String number, lastName;
        final boolean autoOpen;
        Profile(String number, String lastName, boolean autoOpen) {
            this.number = number; this.lastName = lastName; this.autoOpen = autoOpen;
        }
        boolean configured() { return SessionRules.validNumber(number) && SessionRules.validLastName(lastName); }
    }

    static Profile empty() { return new Profile("", "", false); }
    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), FILE_NAME));
    }

    static Profile load(Context context) throws Exception {
        AtomicFile file = file(context);
        if (!file.getBaseFile().exists()) return empty();
        byte[] payload = file.readFully();
        if (payload.length < 30 || payload.length > 4096) throw new IllegalStateException("Invalid profile");
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        if (input.readInt() != 1) throw new IllegalStateException("Unknown profile version");
        byte[] iv = new byte[12]; input.readFully(iv);
        byte[] ciphertext = new byte[input.available()]; input.readFully(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        DataInputStream plain = new DataInputStream(new ByteArrayInputStream(cipher.doFinal(ciphertext)));
        Profile profile = new Profile(plain.readUTF(), plain.readUTF(), plain.readBoolean());
        if (!profile.configured() || plain.available() != 0) throw new IllegalStateException("Invalid profile");
        return profile;
    }

    static void save(Context context, Profile profile) throws Exception {
        if (!profile.configured()) throw new IllegalArgumentException("Invalid account details");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream plain = new DataOutputStream(bytes);
        plain.writeUTF(profile.number); plain.writeUTF(profile.lastName); plain.writeBoolean(profile.autoOpen);
        plain.close();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] iv = cipher.getIV();
        if (iv.length != 12) throw new IllegalStateException("Invalid IV");
        byte[] encrypted = cipher.doFinal(bytes.toByteArray());
        AtomicFile file = file(context);
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream out = new DataOutputStream(stream);
            out.writeInt(1); out.write(iv); out.write(encrypted); out.flush();
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw error;
        }
    }

    static void clear(Context context) throws Exception {
        file(context).delete();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
    }

    private static SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        if (!create) throw new IllegalStateException("Profile key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
}
