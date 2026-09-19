package dev.stella.executer.protection;

import java.lang.reflect.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * 反反编译保护
 * 在字节码层面添加混淆，破坏反编译器输出质量
 */
public final class AntiDecompile {
    private static final Random SECURE_RANDOM = new SecureRandom();
    
    private AntiDecompile() {}
    
    /**
     * 获取混淆后的类名（运行时计算）
     */
    public static String getObfuscatedClassName(String original) {
        long key = RaspProtection.getObfuscationKey();
        int offset = RaspProtection.getObfuscationOffset();
        
        // 基于环境的简单哈希（可被crazy-obfuscator进一步混淆）
        int hash = original.hashCode() ^ (int) key;
        return String.format("a%08x", Math.abs(hash + offset));
    }
    
    /**
     * 获取混淆后的方法名
     */
    public static String getObfuscatedMethodName(String className, String methodName) {
        long key = RaspProtection.getObfuscationKey();
        int hash = (className + methodName).hashCode() ^ (int) (key >>> 32);
        return String.format("m%06x", Math.abs(hash));
    }
    
    /**
     * 运行时字符串解密（混淆器会注入）
     */
    public static String decryptString(byte[] encrypted, int key) {
        byte[] result = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            result[i] = (byte) (encrypted[i] ^ (key + i));
        }
        return new String(result);
    }
    
    /**
     * 运行时数字解密
     */
    public static int decryptInt(int encrypted, int key) {
        return encrypted ^ key;
    }
    
    /**
     * 运行时长整型解密
     */
    public static long decryptLong(long encrypted, long key) {
        return encrypted ^ key;
    }
    
    /**
     * 不透明谓词（总是返回true，但反编译器难以证明）
     */
    public static boolean opaquePredicateTrue() {
        int x = new Object().hashCode();
        return (x * x + x) % 2 == 0; // 数学上总是偶数
    }
    
    /**
     * 不透明谓词（总是返回false，但反编译器难以证明）
     */
    public static boolean opaquePredicateFalse() {
        int x = new Object().hashCode();
        return (x * x + x + 1) % 2 == 0; // 数学上总是奇数
    }
    
    /**
     * 控制流混淆：switch分发器
     */
    public static int dispatch(int state, int input) {
        return (state + input * 0x9e3779b9 + 0x9e3779b9) >>> 16;
    }
    
    /**
     * 反调试钩子（注入到关键方法中）
     */
    public static void antiDebugHook() {
        if (RaspProtection.isTampered()) {
            // 静默失败，不抛出异常
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 生成虚假的类引用（混淆用）
     */
    public static Class<?>[] getPhantomReferences() {
        return new Class<?>[]{
            String.class,
            Integer.class,
            Object.class,
            Runtime.class,
            System.class
        };
    }
    
    /**
     * 验证调用栈（检测反射攻击）
     */
    public static boolean validateCallStack() {
        try {
            StackWalker walker = StackWalker.getInstance();
            return walker.walk(stream -> {
                long count = stream
                    .filter(frame -> {
                        String className = frame.getClassName();
                        return className.contains("reflect") || 
                               className.contains("proxy") ||
                               className.contains("InvocationHandler");
                    })
                    .count();
                return count < 3; // 正常情况反射调用不超过3层
            });
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 环境绑定密钥生成（用于Lua脚本加密）
     */
    public static byte[] generateEnvironmentKey() {
        long envKey = RaspProtection.getObfuscationKey();
        byte[] key = new byte[16];
        
        // 基于环境生成密钥
        key[0] = (byte) (envKey);
        key[1] = (byte) (envKey >>> 8);
        key[2] = (byte) (envKey >>> 16);
        key[3] = (byte) (envKey >>> 24);
        key[4] = (byte) (envKey >>> 32);
        key[5] = (byte) (envKey >>> 40);
        key[6] = (byte) (envKey >>> 48);
        key[7] = (byte) (envKey >>> 56);
        
        // 添加随机性
        for (int i = 8; i < 16; i++) {
            key[i] = (byte) SECURE_RANDOM.nextInt(256);
        }
        
        return key;
    }
    
    /**
     * 字符串混淆（构建时用）
     */
    public static byte[] obfuscateString(String input, int seed) {
        byte[] data = input.getBytes();
        byte[] result = new byte[data.length + 4];
        
        // 添加校验头
        result[0] = (byte) (seed);
        result[1] = (byte) (seed >>> 8);
        result[2] = (byte) (seed >>> 16);
        result[3] = (byte) (seed >>> 24);
        
        for (int i = 0; i < data.length; i++) {
            result[i + 4] = (byte) (data[i] ^ (seed + i * 7));
        }
        
        return result;
    }
    
    /**
     * 字符串解混淆（运行时用）
     */
    public static String deobfuscateString(byte[] obfuscated) {
        if (obfuscated == null || obfuscated.length < 4) return "";
        
        int seed = (obfuscated[0] & 0xFF) | 
                   ((obfuscated[1] & 0xFF) << 8) | 
                   ((obfuscated[2] & 0xFF) << 16) | 
                   ((obfuscated[3] & 0xFF) << 24);
        
        byte[] data = new byte[obfuscated.length - 4];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (obfuscated[i + 4] ^ (seed + i * 7));
        }
        
        return new String(data);
    }
}
