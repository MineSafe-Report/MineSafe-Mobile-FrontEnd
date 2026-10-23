package com.example.javareportingsystem.data;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import com.example.javareportingsystem.data.model.LoggedInUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

//SESSION STORE
//Encryption of login token on device

final class SessionStore
{
    private static final String KEY_ALIAS = "reporting_system_session_key";
    private static final String KEYSTORE = "AndroidKeyStore";
    //Clear old sesion
    private static final int FORMAT_VERSION = 2;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_RECORD_BYTES = 16384;

    private final AtomicFile file;

    SessionStore(Context context)
    {
        File base = new File(context.getApplicationContext().getNoBackupFilesDir(),
            "login_session.bin");
        file = new AtomicFile(base);
    }

    synchronized void save(LoggedInUser user) throws Exception
    {
        JSONArray locations = new JSONArray();
        for (String location : user.getAssignedLocations())
        {
            locations.put(location);
        }
        JSONObject session = new JSONObject();
        session.put("accountId", user.getAccountId());
        session.put("workerId", user.getWorkerId());
        session.put("displayName", user.getDisplayName());
        session.put("role", user.getRole());
        session.put("accessToken", user.getAccessToken());
        session.put("accessExpiresAtMillis", user.getAccessExpiresAtMillis());
        session.put("assignedLocations", locations);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        if (iv.length != IV_LENGTH)
        {
            throw new GeneralSecurityException("Unexpected encryption IV length");
        }
        byte[] encrypted = cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(FORMAT_VERSION);
        record.write(iv);
        record.write(encrypted);
        byte[] bytes = record.toByteArray();
        if (bytes.length > MAX_RECORD_BYTES)
        {
            throw new IOException("Session is too large to store");
        }

        FileOutputStream output = file.startWrite();
        try
        {
            output.write(bytes);
            file.finishWrite(output);
        }
        catch (IOException | RuntimeException e)
        {
            file.failWrite(output);
            throw e;
        }
    }

    synchronized LoggedInUser restore()
    {
        byte[] record;
        try
        {
            record = file.readFully();
        }
        catch (FileNotFoundException e)
        {
            return null;
        }
        catch (IOException e)
        {
            clear();
            return null;
        }
        try
        {
            if (record.length < 1 + IV_LENGTH + TAG_BITS / 8
                || record.length > MAX_RECORD_BYTES || record[0] != FORMAT_VERSION)
            {
                throw new IOException("Invalid saved session");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(record, 1, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(record, 1 + IV_LENGTH,
                record.length - 1 - IV_LENGTH);
            JSONObject session = new JSONObject(new String(decrypted, StandardCharsets.UTF_8));
            JSONArray locationsJson = session.getJSONArray("assignedLocations");
            List<String> locations = new ArrayList<>();
            for (int index = 0; index < locationsJson.length(); index++)
            {
                locations.add(locationsJson.getString(index));
            }
            LoggedInUser user = new LoggedInUser(
                session.getInt("accountId"),
                session.getString("workerId"),
                session.getString("displayName"),
                session.getString("role"),
                session.getString("accessToken"),
                session.getLong("accessExpiresAtMillis"),
                locations);
            if (user.getAccessToken().isEmpty())
            {
                throw new IOException("Saved session has no token");
            }
            return user;
        }
        catch (Exception e)
        {
            clear();
            return null;
        }
    }

    synchronized boolean clear()
    {
        file.delete();
        return !file.getBaseFile().exists();
    }

    private static SecretKey getOrCreateKey() throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (key != null)
        {
            return key;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }
}
