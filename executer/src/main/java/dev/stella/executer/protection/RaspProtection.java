package dev.stella.executer.protection;

import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RASP (Runtime Application Self-Protection)
 * 多层反调试、反反编译、反虚拟机检测
 */
public final class RaspProtection {
    private static final long INIT_TIME = System.currentTimeMillis();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile boolean TAMPERED = false;
    
    // 环境指纹（运行时计算）
    private static long ENV_FINGERPRINT = 0;
    
    private RaspProtection() {}
    
    /**
     * 初始化RASP保护
     */
    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        
        ENV_FINGERPRINT = computeEnvironmentFingerprint();
        
        // 启动后台检测线程
        Thread daemon = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000);
                    if (detectEnvironment()) {
                        TAMPERED = true;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "RASP-Daemon");
        daemon.setDaemon(true);
        daemon.start();
    }
    
    /**
     * 检测是否被篡改/调试
     */
    public static boolean isTampered() {
        return TAMPERED;
    }
    
    /**
     * 环境检测（多项检查）
     */
    private static boolean detectEnvironment() {
        try {
            // 1. Java调试器检测（JDWP）
            if (detectJDWP()) return true;
            
            // 2. Agent检测
            if (detectAgent()) return true;
            
            // 3. 调试器附加检测
            if (detectDebugger()) return true;
            
            // 4. 时间检测（调试会减慢执行）
            if (detectTiming()) return true;
            
            // 5. 内存检测（调试器修改内存布局）
            if (detectMemory()) return true;
            
            // 6. 环境变量检测
            if (detectEnvironmentVariables()) return true;
            
            // 7. 系统属性检测
            if (detectSystemProperties()) return true;
            
            // 8. 文件系统检测
            if (detectFileSystem()) return true;
            
            return false;
        } catch (Exception e) {
            return true; // 异常视为不安全
        }
    }
    
    /**
     * 1. JDWP调试器检测
     */
    private static boolean detectJDWP() {
        try {
            // 检查输入参数
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            List<String> args = runtimeMXBean.getInputArguments();
            for (String arg : args) {
                if (arg.contains("jdwp") || arg.contains("agentlib") || arg.contains("agentpath")) {
                    return true;
                }
            }
            
            // 检查环境变量
            String javaToolOptions = System.getenv("JAVA_TOOL_OPTIONS");
            if (javaToolOptions != null) {
                if (javaToolOptions.contains("jdwp") || javaToolOptions.contains("agentlib")) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 2. Agent检测
     */
    private static boolean detectAgent() {
        try {
            // 检查Instrumentation
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            String name = runtimeMXBean.getName();
            // PID格式检查
            if (name != null && name.matches("\\d+@.*")) {
                // 正常
            }
            
            // 检查Java Agent附件
            String javaAgents = System.getProperty("sun.java.command", "");
            if (javaAgents.contains("-agentlib") || javaAgents.contains("-agentpath")) {
                return true;
            }
            
            // 检查是否被Instrumentation代理附加
            try {
                Class.forName("java.lang.instrument.Instrumentation");
                // 如果存在可能被附加
            } catch (ClassNotFoundException e) {
                // 正常，没有Instrumentation
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 3. 调试器附加检测
     */
    private static boolean detectDebugger() {
        try {
            // 检查Management接口是否被限制
            ManagementFactory.getGarbageCollectorMXBeans();
            ManagementFactory.getMemoryPoolMXBeans();
            
            // 检查是否有调试器附加（通过Runtime）
            Runtime runtime = Runtime.getRuntime();
            int availableProcessors = runtime.availableProcessors();
            long freeMemory = runtime.freeMemory();
            
            // 异常低资源可能表示虚拟化环境
            if (availableProcessors < 2) return true;
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 4. 时间检测（检测单步调试）
     */
    private static boolean detectTiming() {
        try {
            long start = System.nanoTime();
            // 执行简单计算
            int result = 0;
            for (int i = 0; i < 1000; i++) {
                result += i * i;
            }
            long elapsed = System.nanoTime() - start;
            
            // 如果简单的计算花了超过100ms，可能被调试
            if (elapsed > 100_000_000) { // 100ms in nanos
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 5. 内存检测
     */
    private static boolean detectMemory() {
        try {
            // 检查堆内存是否异常
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            
            // 检查非堆内存
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
            
            // 如果堆内存使用异常低，可能是隔离环境
            if (heapUsage.getUsed() < 1024 * 1024) { // 小于1MB
                return true;
            }
            
            // 检查直接内存
            try {
                Class<?> bitsClass = Class.forName("java.nio.Bits");
                java.lang.reflect.Field totalCapacity = bitsClass.getDeclaredField("totalCapacity");
                totalCapacity.setAccessible(true);
                long totalCapacityValue = totalCapacity.getLong(null);
                
                // 如果直接内存为0，可能被限制
                if (totalCapacityValue == 0) return true;
            } catch (Exception e) {
                // 忽略
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 6. 环境变量检测
     */
    private static boolean detectEnvironmentVariables() {
        try {
            Map<String, String> env = System.getenv();
            
            // 检查常见虚拟化/调试环境变量
            String[] suspiciousVars = {
                "HYPERVISOR", "VMWARE", "VBOX", "VIRTUALBOX",
                "XEN", "KVM", "QEMU", "VIRTUAL_ENV",
                "DOCKER", "CONTAINER", "SANDBOX",
                "DEBUG", "PROFILER", "TRACE"
            };
            
            for (String var : suspiciousVars) {
                if (env.containsKey(var)) return true;
            }
            
            // 检查JAVA_TOOL_OPTIONS
            String javaToolOptions = env.get("JAVA_TOOL_OPTIONS");
            if (javaToolOptions != null && !javaToolOptions.isEmpty()) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 7. 系统属性检测
     */
    private static boolean detectSystemProperties() {
        try {
            Properties props = System.getProperties();
            
            // 检查调试相关属性
            String[] debugProps = {
                "sun.java.debugger",
                "java.compiler",
                "jdt.compiler.useSingleThread",
                "org.gradle.debug",
                "idea.debugger.headless"
            };
            
            for (String prop : debugProps) {
                String value = props.getProperty(prop);
                if (value != null && !value.isEmpty()) return true;
            }
            
            // 检查虚拟机名称
            String vmName = props.getProperty("java.vm.name", "").toLowerCase();
            if (vmName.contains("debug") || vmName.contains("experimental")) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 8. 文件系统检测
     */
    private static boolean detectFileSystem() {
        try {
            // 检查是否有反编译工具
            String[] decompilers = {
                "cfr", "procyon", "fernflower", "jadx",
                "jd-gui", "bytecodeviewer", "recaf"
            };
            
            String classpath = System.getProperty("java.class.path", "");
            String classpathLower = classpath.toLowerCase();
            
            for (String decompiler : decompilers) {
                if (classpathLower.contains(decompiler)) return true;
            }
            
            // 检查当前目录是否有可疑工具
            String userDir = System.getProperty("user.dir", "");
            File dir = new File(userDir);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    for (String decompiler : decompilers) {
                        if (name.contains(decompiler)) return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 计算环境指纹
     */
    private static long computeEnvironmentFingerprint() {
        try {
            long hash = 17;
            hash = 31 * hash + System.getProperty("java.version", "").hashCode();
            hash = 31 * hash + System.getProperty("os.name", "").hashCode();
            hash = 31 * hash + System.getProperty("os.arch", "").hashCode();
            hash = 31 * hash + System.getProperty("user.name", "").hashCode();
            hash = 31 * hash + System.getProperty("user.dir", "").hashCode();
            hash = 31 * hash + Runtime.getRuntime().availableProcessors();
            hash = 31 * hash + Runtime.getRuntime().totalMemory();
            return hash;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * 获取混淆密钥（基于环境，不可预测）
     */
    public static long getObfuscationKey() {
        return ENV_FINGERPRINT ^ INIT_TIME;
    }
    
    /**
     * 获取混淆偏移量
     */
    public static int getObfuscationOffset() {
        return (int) (INIT_TIME & 0xFF);
    }
    
    /**
     * 执行保护（混淆代码用）
     */
    public static void protect() {
        if (!INITIALIZED.get()) {
            initialize();
        }
    }
    
    /**
     * 验证环境完整性
     */
    public static boolean validateEnvironment() {
        return !TAMPERED && (System.currentTimeMillis() - INIT_TIME) < 3600000; // 1小时内
    }
}
