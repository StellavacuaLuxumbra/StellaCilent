package dev.stella.executer.protection;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES string encryption for anti-RE protection.
 * Used by crazy-obfuscator Gradle task to encrypt strings at build time.
 * Runtime decryption is handled by the obfuscator's injected decryptor.
 */
public final class StringEncryptor {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static byte[] key;
    
    private StringEncryptor() {}
    
    /**
     * Generate a new AES key
     */
    public static byte[] generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(128, new SecureRandom());
        SecretKey secretKey = keyGen.generateKey();
        return secretKey.getEncoded();
    }
    
    /**
     * Set the encryption key
     */
    public static void setKey(byte[] keyBytes) {
        key = keyBytes;
    }
    
    /**
     * Encrypt a string
     */
    public static String encrypt(String plaintext) throws Exception {
        if (key == null) key = generateKey();
        
        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }
    
    /**
     * Decrypt a string
     */
    public static String decrypt(String ciphertext) throws Exception {
        if (key == null) throw new IllegalStateException("Key not set");
        
        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, "UTF-8");
    }
    
    /**
     * XOR-based string encryption (simpler, faster)
     */
    public static String xorEncrypt(String plaintext, byte[] key) {
        byte[] data = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return Base64.getEncoder().encodeToString(result);
    }
    
    /**
     * XOR-based string decryption
     */
    public static String xorDecrypt(String ciphertext, byte[] key) {
        byte[] data = Base64.getDecoder().decode(ciphertext);
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return new String(result, java.nio.charset.StandardCharsets.UTF_8);
    }
}
