package dev.stella.mod.gui.fonts;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FontSelector - 字体选择器工具类
 * 扫描可用的字体资源
 */
public class FontSelector {
    
    /**
     * 获取所有可用的字体名称列表
     */
    public static List<String> getAvailableFonts() {
        List<String> fonts = new ArrayList<>();

        // 1. 添加默认选项
        fonts.add("default");

        // 2. 添加 Stella 内置资源字体（硬编码列表，确保始终可用）
        fonts.add("songti");    // 宋体(资源字体)

        // 3. 扫描自定义字体资源 (assets/stella/font/)
        try {
            ClassLoader classLoader = FontSelector.class.getClassLoader();
            java.net.URL resource = classLoader.getResource("assets/stella/font");
            if (resource != null) {
                java.net.URI uri = resource.toURI();
                java.nio.file.Path path;
                if (uri.getScheme().equals("jar")) {
                    try (java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(uri, java.util.Collections.emptyMap())) {
                        java.nio.file.Path resourcePath = fs.getPath("/assets/stella/font");
                        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(resourcePath, 1)) {
                            walk.filter(java.nio.file.Files::isRegularFile)
                                .forEach(p -> {
                                    String name = p.getFileName().toString();
                                    if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                                        String fontName = name.substring(0, name.lastIndexOf('.'));
                                        if (!fonts.contains(fontName)) {
                                            fonts.add(fontName);
                                        }
                                    }
                                });
                        }
                    }
                } else {
                    java.nio.file.Path resourcePath = java.nio.file.Paths.get(uri);
                    try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(resourcePath, 1)) {
                        walk.filter(java.nio.file.Files::isRegularFile)
                            .forEach(p -> {
                                String name = p.getFileName().toString();
                                if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                                    String fontName = name.substring(0, name.lastIndexOf('.'));
                                    if (!fonts.contains(fontName)) {
                                        fonts.add(fontName);
                                    }
                                }
                            });
                    }
                }
            }
        } catch (Exception e) {
            // 忽略扫描错误
        }

        // 4. 扫描 Windows 系统字体
        File fontDir = new File("C:\\Windows\\Fonts");
        if (fontDir.exists() && fontDir.isDirectory()) {
            File[] fontFiles = fontDir.listFiles();
            if (fontFiles != null) {
                for (File file : fontFiles) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc")) {
                        String fontName = name.substring(0, name.lastIndexOf('.'));
                        // 只添加常用的中文字体和英文字体
                        if (isCommonFont(fontName) && !fonts.contains(fontName)) {
                            fonts.add(fontName);
                        }
                    }
                }
            }
        }

        // 5. 添加常用的后备字体
        fonts.add("msyh");      // 微软雅黑
        fonts.add("simsun");    // 宋体
        fonts.add("simhei");    // 黑体
        fonts.add("arial");
        fonts.add("segoeui");
        fonts.add("verdana");

        // 去重
        return fonts.stream().distinct().collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 判断是否是常用字体
     */
    private static boolean isCommonFont(String fontName) {
        String lower = fontName.toLowerCase();
        // 中文字体
        if (lower.contains("msyh") || lower.contains("simhei") || lower.contains("simsun") || 
            lower.contains("yahei") || lower.contains("kaiti") || lower.contains("fangsong") ||
            lower.contains("microsoft")) {
            return true;
        }
        // 英文字体
        if (lower.contains("arial") || lower.contains("segoe") || lower.contains("verdana") ||
            lower.contains("tahoma") || lower.contains("times") || lower.contains("calibri") ||
            lower.contains("consolas") || lower.contains("courier") || lower.contains("trebuchet")) {
            return true;
        }
        return false;
    }
}
