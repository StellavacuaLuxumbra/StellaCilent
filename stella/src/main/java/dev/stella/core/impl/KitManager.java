package dev.stella.core.impl;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import dev.stella.api.IAddon;
import dev.stella.api.utils.Wrapper;
import dev.stella.core.Manager;
import dev.stella.core.impl.IManager;
import dev.stella.stella;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class KitManager implements IManager, Wrapper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ADDONS_DIR = FabricLoader.getInstance().getGameDir().resolve("stella/addons");
    
    private final Map<String, IAddon> kits = new HashMap<>();
    public static KitManager INSTANCE;
    
    // KitManager integration
    private int totalKits = 0;
    private final List<IAddon> kitsList = new ArrayList<>();
    
    // Kit management functionality
    private static final String KITS_FOLDER = "stella/kits/";
    private static final String SELECTED_FILE = KITS_FOLDER + "selected.txt";
    
    private String selectedKit = "";
    
    public KitManager() {
        INSTANCE = this;
        try {
            if (!Files.exists(ADDONS_DIR)) {
                Files.createDirectories(ADDONS_DIR);
            }
        } catch (IOException e) {
            LogUtils.getLogger().error("Failed to create addons directory", e);
        }
    }

    @Override
    public void init() {
        initAddons();
    }

    @Override
    public void shutdown() {
        shutDown();
    }

    public void incrementKitCount() {
        totalKits++;
    }

    public int getTotalKits() {
        return totalKits;
    }

    public void addKit(IAddon kit) {
        kits.put(kit.getName(), kit);
        kitsList.add(kit);
    }

    public List<IAddon> getKits() {
        return kitsList;
    }

    public void registerKitManager(Object kitManager) {
        // Stub for addon kit manager registration
    }



    public void initAddons() {
        LogUtils.getLogger().info("Starting addon initialization.");

        for (EntrypointContainer<IAddon> entrypoint : FabricLoader.getInstance().getEntrypointContainers("stella", IAddon.class)) {
            IAddon kit = entrypoint.getEntrypoint(); // Initialize the kit

            try {
                LogUtils.getLogger().info("Initializing addon: " + kit.getClass().getName());         
                LogUtils.getLogger().debug("Addon class loader: " + kit.getClass().getClassLoader());
                kit.onInitialize(); // Call the onInitialize method
                LogUtils.getLogger().info("Kit initialized successfully: " + kit.getClass().getName());

                incrementKitCount(); // Increment the total kit count
                LogUtils.getLogger().debug("Kit count incremented.");

                addKit(kit);
                LogUtils.getLogger().debug("Kit added to manager.");
                stella.EVENT_BUS.registerLambdaFactory((lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

                // Register Modules
                if (kit.getModules() != null)
                    kit.getModules().stream().filter(Objects::nonNull).forEach(module -> {
                        try {
                            LogUtils.getLogger().info("Registering module: " + module.getClass().getName());
                            LogUtils.getLogger().debug("Module class loader: " + module.getClass().getClassLoader());
                            stella.MODULE.addModule(module);
                            LogUtils.getLogger().info("Module registered successfully: " + module.getClass().getName());
                        } catch (Exception e) {
                            LogUtils.getLogger().error("Error registering module: " + module.getClass().getName(), e);
                        }
                    });

                // Register Commands
                if (kit.getCommands() != null)
                    kit.getCommands().stream().filter(Objects::nonNull).forEach(command -> {
                        try {
                            LogUtils.getLogger().info("Registering command: " + command.getClass().getName());
                            LogUtils.getLogger().debug("Command class loader: " + command.getClass().getClassLoader());
                            stella.COMMAND.registerCommand(command);
                            LogUtils.getLogger().info("Command registered successfully: " + command.getClass().getName());
                        } catch (Exception e) {
                            LogUtils.getLogger().error("Error registering command: " + command.getClass().getName(), e);
                        }
                    });

                // Register HUD Elements
                if (kit.getHudElements() != null)
                    kit.getHudElements().stream().filter(Objects::nonNull).forEach(hudElement -> {
                        try {
                            LogUtils.getLogger().info("Registering HUD element: " + hudElement.getClass().getName());
                            LogUtils.getLogger().debug("HUD element class loader: " + hudElement.getClass().getClassLoader());
                            stella.MODULE.addModule(hudElement);
                            LogUtils.getLogger().info("HUD element registered successfully: " + hudElement.getClass().getName());
                        } catch (Exception e) {
                            LogUtils.getLogger().error("Error registering HUD element: " + hudElement.getClass().getName(), e);
                        }
                    });

                // Register KitManager
                if (kit.getKitManager() != null) {
                    try {
                        LogUtils.getLogger().info("Registering KitManager: " + kit.getKitManager().getClass().getName());
                        stella.KIT.registerKitManager(kit.getKitManager());
                        LogUtils.getLogger().info("KitManager registered successfully: " + kit.getKitManager().getClass().getName());
                    } catch (Exception e) {
                        LogUtils.getLogger().error("Error registering KitManager: " + kit.getKitManager().getClass().getName(), e);
                    }
                }

            } catch (Exception e) {
                LogUtils.getLogger().error("Error initializing addon: " + kit.getClass().getName(), e);
            }
        }

        LogUtils.getLogger().info("Addon initialization complete.");
    }

    public void shutDown() {
        for (IAddon kit : getKits()) {
            try {
                kit.onShutdown();
            } catch (Exception e) {
                LogUtils.getLogger().error("Error running kit onShutdown method: " + kit.getClass().getName(), e);
            }
        }
    }
    
    // Kit management methods for equipment kits
    public boolean saveKit(String name) {
        if (Wrapper.mc.player == null) return false;
        
        try {
            File folder = new File(KITS_FOLDER);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            
            File file = new File(KITS_FOLDER + name + ".json");
            
            JsonObject kitData = new JsonObject();
            kitData.addProperty("name", name);
            kitData.addProperty("created", System.currentTimeMillis());
            
            JsonArray items = new JsonArray();
            
            for (int i = 0; i < Wrapper.mc.player.getInventory().main.size(); i++) {
                ItemStack stack = Wrapper.mc.player.getInventory().main.get(i);
                if (!stack.isEmpty()) {
                    items.add(serializeItemStack(stack, i));
                }
            }
            
            for (int i = 0; i < Wrapper.mc.player.getInventory().armor.size(); i++) {
                ItemStack stack = Wrapper.mc.player.getInventory().armor.get(i);
                if (!stack.isEmpty()) {
                    items.add(serializeItemStack(stack, 36 + i));
                }
            }
            
            ItemStack offhand = Wrapper.mc.player.getInventory().offHand.get(0);
            if (!offhand.isEmpty()) {
                items.add(serializeItemStack(offhand, 40));
            }
            
            kitData.add("items", items);
            
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(GSON.toJson(kitData));
            writer.close();
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public KitItem[] loadKit(String name) {
        File file = new File(KITS_FOLDER + name + ".json");
        if (!file.exists()) return null;
        
        try {
            String jsonString = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            JsonObject kitData = GSON.fromJson(jsonString, JsonObject.class);
            JsonArray items = kitData.getAsJsonArray("items");
            
            KitItem[] kitItems = new KitItem[items.size()];
            int index = 0;
            for (JsonElement element : items) {
                KitItem item = deserializeItemStack(element.getAsJsonObject());
                if (item != null) {
                    kitItems[index++] = item;
                }
            }
            return kitItems;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public boolean deleteKit(String name) {
        File file = new File(KITS_FOLDER + name + ".json");
        if (!file.exists()) return false;
        
        boolean deleted = file.delete();
        if (deleted && name.equals(selectedKit)) {
            setSelectedKit("");
        }
        return deleted;
    }
    
    public String[] getKitNames() {
        File folder = new File(KITS_FOLDER);
        if (!folder.exists()) {
            return new String[0];
        }
        
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return new String[0];
        
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName().replace(".json", "");
        }
        return names;
    }
    
    public boolean kitExists(String name) {
        return new File(KITS_FOLDER + name + ".json").exists();
    }
    
    public void setSelectedKit(String name) {
        selectedKit = name;
        saveSelectedToFile();
    }
    
    public String getSelectedKit() {
        if (selectedKit.isEmpty()) {
            loadSelectedFromFile();
        }
        return selectedKit;
    }
    
    public KitItem[] getSelectedKitItems() {
        String selected = getSelectedKit();
        if (selected.isEmpty()) return null;
        return loadKit(selected);
    }
    
    private void saveSelectedToFile() {
        try {
            File file = new File(SELECTED_FILE);
            file.getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(selectedKit);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void loadSelectedFromFile() {
        try {
            File file = new File(SELECTED_FILE);
            if (!file.exists()) {
                selectedKit = "";
                return;
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            selectedKit = reader.readLine();
            reader.close();
            if (selectedKit == null) selectedKit = "";
        } catch (IOException e) {
            selectedKit = "";
        }
    }
    
    private JsonObject serializeItemStack(ItemStack stack, int slot) {
        JsonObject obj = new JsonObject();
        obj.addProperty("slot", slot);
        obj.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
        obj.addProperty("count", stack.getCount());
        
        if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
            NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            if (!nbt.isEmpty()) {
                obj.addProperty("nbt", nbt.toString());
            }
        }
        
        return obj;
    }
    
    private KitItem deserializeItemStack(JsonObject obj) {
        try {
            int slot = obj.get("slot").getAsInt();
            String id = obj.get("id").getAsString();
            int count = obj.get("count").getAsInt();
            String nbtString = obj.has("nbt") ? obj.get("nbt").getAsString() : null;
            
            return new KitItem(slot, id, count, nbtString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static class KitItem {
        public final int slot;
        public final String itemId;
        public final int count;
        public final String nbt;
        
        public KitItem(int slot, String itemId, int count, String nbt) {
            this.slot = slot;
            this.itemId = itemId;
            this.count = count;
            this.nbt = nbt;
        }
        
        public ItemStack createStack() {
            try {
                Identifier id = Identifier.of(itemId);
                net.minecraft.item.Item item = Registries.ITEM.get(id);
                ItemStack stack = new ItemStack(item, count);
                
                if (nbt != null && !nbt.isEmpty()) {
                    try {
                        NbtCompound nbtCompound = StringNbtReader.parse(nbt);
                        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbtCompound);
                    } catch (Exception e) {
                        // 如果解析失败，记录错误但不抛出异常
                        System.err.println("Failed to parse NBT for item " + itemId + ": " + e.getMessage());
                    }
                }
                
                return stack;
            } catch (Exception e) {
                e.printStackTrace();
                return ItemStack.EMPTY;
            }
        }
    }
}