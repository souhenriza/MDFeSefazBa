package com.souhenriza.sefazba.infrastructure.config;

import com.google.crypto.tink.*;
import com.google.crypto.tink.aead.AeadConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
public class CryptoService {

    private final Aead aead;

     public CryptoService(String keyPath) {
         try {
             AeadConfig.register();

             KeysetHandle keysetHandle;

             File keyFile = new File(keyPath, "secret-key.json");

             if (keyFile.exists() && keyFile.length() > 0) {
                 try (InputStream inputStream = new FileInputStream(keyFile)) {
                     keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withInputStream(inputStream));
                 }
                 log.debug("Using the key at {}", keyFile.getAbsolutePath());
             } else {
                 keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
                 keyFile.getAbsoluteFile().getParentFile().mkdirs();
                 log.warn("No key found at {}; generated a new one", keyFile.getAbsolutePath());
                 try (OutputStream outputStream = new FileOutputStream(keyFile)) {
                     CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(outputStream));
                 }
             }


             this.aead = keysetHandle.getPrimitive(Aead.class);

         } catch (GeneralSecurityException | IOException e) {
             throw new RuntimeException(e);
         }
     }

    public String encrypt(String text) {
        try {
            byte[] cipherText = aead.encrypt(text.getBytes(StandardCharsets.UTF_8), null);

            return Base64.getEncoder().encodeToString(cipherText);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public String decrypt(String cipheredText) {
         try {
            byte[] cipheredBytes = Base64.getDecoder().decode(cipheredText.getBytes(StandardCharsets.UTF_8));

            byte[] plaintextBytes = aead.decrypt(cipheredBytes, null);

            return new String(plaintextBytes, StandardCharsets.UTF_8);
         } catch (GeneralSecurityException e) {
             throw new RuntimeException(e.getMessage(), e.getCause());
         }
    }

}
