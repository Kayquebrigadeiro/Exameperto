package br.com.exameperto.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class DataProtector {
    private final byte[] dataKey, searchKey;
    private final SecureRandom random = new SecureRandom();

    DataProtector(@Value("${security.data-key:}") String data,
                  @Value("${security.search-key:}") String search) {
        dataKey = decode(data); searchKey = decode(search);
        if (dataKey != null && searchKey != null && MessageDigest.isEqual(dataKey, searchKey))
            throw new IllegalStateException("Use chaves distintas para cifra e busca.");
    }

    static byte[] decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException ex) { throw new IllegalStateException("Chave deve ter 32 bytes aleatórios em Base64."); }
    }

    boolean configured() { return dataKey != null && searchKey != null; }

    byte[] encrypt(String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(new byte[]{1});
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + iv.length + encrypted.length).put((byte)1).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Proteção de dados indisponível."); }
    }

    String decrypt(byte[] value) {
        try {
            ByteBuffer input = ByteBuffer.wrap(value);
            if (input.get() != 1) throw new IllegalStateException("Versão de chave indisponível.");
            byte[] iv = new byte[12]; input.get(iv);
            byte[] encrypted = new byte[input.remaining()]; input.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(new byte[]{1});
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Dados protegidos inválidos."); }
    }

    byte[] lookup(String normalized) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(searchKey, "HmacSHA256"));
            return mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Busca indisponível."); }
    }
    String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    byte[] tokenHash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}
