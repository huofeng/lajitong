package com.yei_bai.trashplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

@SuppressWarnings("ALL")
public class DataManager {

    private final JavaPlugin plugin;
    private File dataFile;
    private FileConfiguration data;

    private final Map<String, Double> customPrices = new HashMap<>();
    private final List<TrashItem> publicTrashItems = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        ConfigurationSerialization.registerClass(TrashItem.class, "TrashItem");
    }

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");

        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "无法创建数据文件", e);
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);

        // 加载自定义价格
        if (data.contains("custom-prices")) {
            ConfigurationSection pricesSection = data.getConfigurationSection("custom-prices");
            if (pricesSection != null) {
                for (String key : pricesSection.getKeys(false)) {
                    customPrices.put(key.toUpperCase(), pricesSection.getDouble(key));
                }
            }
        }

        // 加载公共垃圾桶物品
        if (data.contains("public-trash-items")) {
            List<?> items = data.getList("public-trash-items");
            if (items != null) {
                for (Object obj : items) {
                    if (obj instanceof TrashItem) {
                        publicTrashItems.add((TrashItem) obj);
                    }
                }
            }
        }

        plugin.getLogger().info("已加载 " + publicTrashItems.size() + " 个公共垃圾桶物品");
        plugin.getLogger().info("已加载 " + customPrices.size() + " 个自定义价格");
    }

    public void saveData() {
        if (data == null || dataFile == null) {
            return;
        }

        // 保存自定义价格
        for (Map.Entry<String, Double> entry : customPrices.entrySet()) {
            data.set("custom-prices." + entry.getKey(), entry.getValue());
        }

        // 保存公共垃圾桶物品
        data.set("public-trash-items", new ArrayList<>(publicTrashItems));

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存数据文件", e);
        }
    }

    public void addPublicTrashItem(TrashItem trashItem) {
        publicTrashItems.add(trashItem);
        saveData();
    }

    public void addPublicTrashItem(ItemStack item, String owner) {
        TrashItem trashItem = new TrashItem(item, owner, new Date());
        publicTrashItems.add(trashItem);
        saveData();
    }

    public boolean removePublicTrashItem(TrashItem trashItem) {
        boolean result = publicTrashItems.remove(trashItem);
        if (result) {
            saveData(); // 立即保存
        }
        return result;
    }

    public void removePublicTrashItem(int index) {
        if (index >= 0 && index < publicTrashItems.size()) {
            publicTrashItems.remove(index);
            saveData(); // 立即保存
        }
    }

    public void clearPublicTrash() {
        publicTrashItems.clear();
        saveData(); // 立即保存
    }

    public List<TrashItem> getPublicTrashItems() {
        return new ArrayList<>(publicTrashItems);
    }

    public List<TrashItem> getPublicTrashItems(int page, int itemsPerPage) {
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, publicTrashItems.size());

        if (start >= publicTrashItems.size()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(publicTrashItems.subList(start, end));
    }

    public int getTotalPages(int itemsPerPage) {
        if (publicTrashItems.isEmpty() || itemsPerPage <= 0) {
            return 1;
        }

        return (int) Math.ceil((double) publicTrashItems.size() / itemsPerPage);
    }

    public int getPublicTrashSize() {
        return publicTrashItems.size();
    }

    // 硬代码不限数量存储
    public boolean isPublicTrashFull() {
        return false; // 永不限制
    }

    public void setCustomPrice(String material, double price) {
        customPrices.put(material.toUpperCase(), price);
    }

    public Double getCustomPrice(String material) {
        return customPrices.get(material.toUpperCase());
    }

    public Map<String, Double> getCustomPrices() {
        return new HashMap<>(customPrices);
    }

    public String formatDate(Date date) {
        synchronized (dateFormat) {
            return dateFormat.format(date);
        }
    }

    public void reload() {
        loadData();
    }
}