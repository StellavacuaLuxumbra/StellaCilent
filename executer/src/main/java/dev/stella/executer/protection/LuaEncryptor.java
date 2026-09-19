package dev.stella.executer.protection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;

/**
 * Lua script encryption for anti-RE protection.
 * Encrypts .lua files to .luenc at build time.
 * Decryption is handled by LuaScriptLoader at runtime.
 */
public final class LuaEncryptor {
    private static final byte[] DEFAULT_KEY = {
        (byte)0x53, (byte)0x74, (byte)0x65, (byte)0x6C, 
        (byte)0x6C, (byte)0x61, (byte)0x43, (byte)0x6C
    };
    
    private LuaEncryptor() {}
    
    /**
     * XOR encrypt Lua script content
     */
    public static byte[] encrypt(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }
    
    /**
     * XOR decrypt Lua script content
     */
    public static byte[] decrypt(byte[] data, byte[] key) {
        return encrypt(data, key); // XOR is symmetric
    }
    
    /**
     * Generate a random key
     */
    public static byte[] generateKey(int length) {
        byte[] key = new byte[length];
        new SecureRandom().nextBytes(key);
        return key;
    }
    
    /**
     * Encrypt a .lua file to .luenc
     */
    public static void encryptFile(Path inputFile, Path outputFile, byte[] key) throws IOException {
        byte[] luaBytes = Files.readAllBytes(inputFile);
        byte[] encrypted = encrypt(luaBytes, key);
        Files.write(outputFile, encrypted);
    }
    
    /**
     * Encrypt all Lua scripts in a directory
     */
    public static void encryptDirectory(Path inputDir, Path outputDir, byte[] key) throws IOException {
        Files.createDirectories(outputDir);
        
        String[] modules = {
            "KillAura", "CrystalAura", "Fullbright", "Scaffold",
            "AutoTotem", "ChestStealer", "ElytraFly"
        };
        
        for (String module : modules) {
            Path inputFile = inputDir.resolve(module + ".lua");
            Path outputFile = outputDir.resolve(module + ".luenc");
            
            if (Files.exists(inputFile)) {
                encryptFile(inputFile, outputFile, key);
                System.out.println("Encrypted: " + module + ".lua -> " + module + ".luenc");
            } else {
                System.err.println("Warning: " + inputFile + " not found");
            }
        }
    }
    
    /**
     * Main method for build-time encryption
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: LuaEncryptor <input_dir> <output_dir> [key_hex]");
            System.exit(1);
        }
        
        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        
        byte[] key;
        if (args.length > 2) {
            // Parse hex key
            String hex = args[2];
            key = new byte[hex.length() / 2];
            for (int i = 0; i < key.length; i++) {
                key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
        } else {
            key = DEFAULT_KEY;
        }
        
        encryptDirectory(inputDir, outputDir, key);
        
        // Print key for reference
        StringBuilder sb = new StringBuilder();
        for (byte b : key) {
            sb.append(String.format("%02x", b));
        }
        System.out.println("Encryption key: " + sb.toString());
    }
}
