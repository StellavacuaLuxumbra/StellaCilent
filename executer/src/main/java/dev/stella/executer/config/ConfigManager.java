package dev.stella.executer.config;

import com.google.gson.*;
import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;
import dev.stella.executer.gui.hud.HudElement;
import dev.stella.executer.gui.hud.HudManager;
import dev.stella.executer.gui.setting.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ConfigManager {
    private static ConfigManager instance;
    private static final Path CONFIG_DIR = Paths.get(
            System.getProperty("user.home"), ".stella-client");
    private static final Path DEFAULT_CONFIG = CONFIG_DIR.resolve("default.json");

    private ConfigManager() {}

    public static ConfigManager getInstance() {
        if (instance == null) instance = new ConfigManager();
        return instance;
    }

    public void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save(String name) {
        Path file = CONFIG_DIR.resolve(name + ".json");
        JsonObject root = new JsonObject();

        // modules
        JsonObject modulesObj = new JsonObject();
        for (Module m : ModuleManager.getInstance().getModules()) {
            JsonObject modObj = new JsonObject();
            modObj.addProperty("enabled", m.isEnabled());

            JsonObject settingsObj = new JsonObject();
            for (Setting s : m.getSettings()) {
                if (s instanceof BooleanSetting bs) {
                    settingsObj.addProperty(bs.getName(), bs.getValue());
                } else if (s instanceof SliderSetting ss) {
                    settingsObj.addProperty(ss.getName(), ss.getValue());
                } else if (s instanceof EnumSetting es) {
                    settingsObj.addProperty(es.getName(), es.getValue().name());
                } else if (s instanceof ColorSetting cs) {
                    settingsObj.addProperty(cs.getName(), cs.getValue());
                    settingsObj.addProperty(cs.getName() + "_rainbow", cs.isRainbow());
                } else if (s instanceof BindSetting bs2) {
                    settingsObj.addProperty(bs2.getName(), bs2.getValue());
                }
            }
            modObj.add("settings", settingsObj);
            modulesObj.add(m.getName(), modObj);
        }
        root.add("modules", modulesObj);

        // hud
        JsonObject hudObj = new JsonObject();
        for (HudElement e : HudManager.getInstance().getElements()) {
            JsonObject eObj = new JsonObject();
            eObj.addProperty("visible", e.isVisible());
            eObj.addProperty("x", e.getX());
            eObj.addProperty("y", e.getY());
            hudObj.add(e.getClass().getSimpleName(), eObj);
        }
        root.add("hud", hudObj);

        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load(String name) {
        Path file = CONFIG_DIR.resolve(name + ".json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // modules
            if (root.has("modules")) {
                JsonObject modulesObj = root.getAsJsonObject("modules");
                for (Module m : ModuleManager.getInstance().getModules()) {
                    if (modulesObj.has(m.getName())) {
                        JsonObject modObj = modulesObj.getAsJsonObject(m.getName());
                        m.setEnabled(modObj.get("enabled").getAsBoolean());

                        if (modObj.has("settings")) {
                            JsonObject settingsObj = modObj.getAsJsonObject("settings");
                            for (Setting s : m.getSettings()) {
                                if (settingsObj.has(s.getName())) {
                                    if (s instanceof BooleanSetting bs) {
                                        bs.setValue(settingsObj.get(bs.getName()).getAsBoolean());
                                    } else if (s instanceof SliderSetting ss) {
                                        ss.setValue(settingsObj.get(ss.getName()).getAsDouble());
                                    } else if (s instanceof EnumSetting es) {
                                        String val = settingsObj.get(es.getName()).getAsString();
                                        for (Enum<?> e : es.getValues()) {
                                            if (e.name().equals(val)) {
                                                es.setValue(e);
                                                break;
                                            }
                                        }
                                    } else if (s instanceof ColorSetting cs) {
                                        cs.setValue(settingsObj.get(cs.getName()).getAsInt());
                                        if (settingsObj.has(cs.getName() + "_rainbow")) {
                                            cs.setRainbow(settingsObj.get(cs.getName() + "_rainbow").getAsBoolean());
                                        }
                                    } else if (s instanceof BindSetting bs2) {
                                        bs2.setValue(settingsObj.get(bs2.getName()).getAsInt());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // hud
            if (root.has("hud")) {
                JsonObject hudObj = root.getAsJsonObject("hud");
                for (HudElement e : HudManager.getInstance().getElements()) {
                    if (hudObj.has(e.getClass().getSimpleName())) {
                        JsonObject eObj = hudObj.getAsJsonObject(e.getClass().getSimpleName());
                        e.setVisible(eObj.get("visible").getAsBoolean());
                        e.setPosition(eObj.get("x").getAsInt(), eObj.get("y").getAsInt());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveDefault() { save("default"); }
    public void loadDefault() { load("default"); }

    public String[] listConfigs() {
        try {
            return Files.list(CONFIG_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .toArray(String[]::new);
        } catch (IOException e) {
            return new String[0];
        }
    }
}
