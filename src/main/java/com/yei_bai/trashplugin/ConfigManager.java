package com.yei_bai.trashplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ALL")
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // 配置值缓存
    private List<String> enabledWorlds = new ArrayList<>();
    private boolean debug = false;

    // GUI设置
    private final Map<String, Object> personalGUISettings = new HashMap<>();
    private final Map<String, Object> publicGUISettings = new HashMap<>();

    // 扫地设置
    private boolean sweeperEnabled = true;
    private int sweeperInterval = 300;
    private int sweeperRadius = 10;
    private String sweeperMessage = "";
    private boolean broadcastSweep = true;
    private List<String> blacklist = new ArrayList<>();

    // 公共垃圾桶设置
    private int autoRefresh = 3600;
    private int saveInterval = 300;
    private int itemsPerPage = 45;

    // 价格设置
    private double defaultPrice = 1.0;
    private final Map<String, Double> customPrices = new HashMap<>();

    // 经济设置
    private boolean useVault = true;
    private String currencySymbol = "金币";

    // 语言设置
    private String language = "zh_CN";

    // 背包最大插槽（GUI配置兜底用）
    private static final int MAX_INVENTORY_SLOT = 53;
    private static final int MIN_INVENTORY_SLOT = 0;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // 重新加载配置
        plugin.reloadConfig();
        config = plugin.getConfig();

        // 加载基本设置（新增：enabledWorlds兜底）
        loadBasicSettings();

        // 加载GUI设置（新增：材质规范化、插槽校验）
        loadGUISettings();

        // 加载扫地设置（新增：参数非负校验）
        loadSweeperSettings();

        // 加载公共垃圾桶设置（新增：参数非负校验）
        loadPublicTrashSettings();

        // 加载价格设置
        loadPriceSettings();

        // 加载经济设置
        useVault = config.getBoolean("economy.use-vault", true);
        currencySymbol = config.getString("economy.currency-symbol", "金币");

        // 加载语言设置
        language = config.getString("language", "zh_CN");
    }

    /**
     * 加载基本设置（新增兜底逻辑）
     */
    private void loadBasicSettings() {
        enabledWorlds = config.getStringList("enabled-worlds");
        // 若启用世界列表为空，默认添加主世界（避免所有世界禁用）
        if (enabledWorlds.isEmpty()) {
            enabledWorlds.add("world");
            plugin.getLogger().warning("启用世界列表为空，默认添加主世界「world」");
        }
        debug = config.getBoolean("debug", false);
    }

    private void loadGUISettings() {
        ConfigurationSection guiSection = config.getConfigurationSection("gui");
        if (guiSection != null) {
            // 个人垃圾桶设置
            ConfigurationSection personalSection = guiSection.getConfigurationSection("personal");
            if (personalSection != null) {
                personalGUISettings.put("title", personalSection.getString("title", "&8垃圾桶"));
                // GUI行数兜底（1-6行）
                int personalRows = personalSection.getInt("rows", 6);
                personalRows = Math.max(1, Math.min(6, personalRows));
                personalGUISettings.put("rows", personalRows);

                ConfigurationSection decorationSection = personalSection.getConfigurationSection("decoration");
                if (decorationSection != null) {
                    // 材质名称规范化（转大写）
                    String decoMaterial = decorationSection.getString("material", "BLACK_STAINED_GLASS_PANE").toUpperCase();
                    personalGUISettings.put("decoration-material", decoMaterial);
                    personalGUISettings.put("decoration-name", decorationSection.getString("name", "&8╏"));
                    personalGUISettings.put("decoration-lore", decorationSection.getStringList("lore"));
                }
            }

            // 公共垃圾桶设置
            ConfigurationSection publicSection = guiSection.getConfigurationSection("public");
            if (publicSection != null) {
                publicGUISettings.put("title", publicSection.getString("title", "&6公共垃圾桶"));
                // GUI行数兜底（1-6行）
                int publicRows = publicSection.getInt("rows", 6);
                publicRows = Math.max(1, Math.min(6, publicRows));
                publicGUISettings.put("rows", publicRows);

                // 每页物品数兜底（1-45）
                itemsPerPage = publicSection.getInt("items-per-page", 45);
                itemsPerPage = Math.max(1, Math.min(45, itemsPerPage));

                ConfigurationSection decorationSection = publicSection.getConfigurationSection("decoration");
                if (decorationSection != null) {
                    // 材质名称规范化（转大写）
                    String decoMaterial = decorationSection.getString("material", "GRAY_STAINED_GLASS_PANE").toUpperCase();
                    publicGUISettings.put("decoration-material", decoMaterial);
                    publicGUISettings.put("decoration-name", decorationSection.getString("name", "&8╏"));
                    publicGUISettings.put("decoration-lore", decorationSection.getStringList("lore"));
                }

                // 控制按钮
                ConfigurationSection controlsSection = publicSection.getConfigurationSection("controls");
                if (controlsSection != null) {
                    // 上一页配置（材质规范化+插槽校验）
                    String prevMaterial = controlsSection.getString("previous-page.material", "ARROW").toUpperCase();
                    publicGUISettings.put("previous-page-material", prevMaterial);
                    publicGUISettings.put("previous-page-name", controlsSection.getString("previous-page.name", "&a上一页"));
                    int prevSlot = controlsSection.getInt("previous-page.slot", 45);
                    prevSlot = Math.max(MIN_INVENTORY_SLOT, Math.min(MAX_INVENTORY_SLOT, prevSlot));
                    publicGUISettings.put("previous-page-slot", prevSlot);

                    // 下一页配置（材质规范化+插槽校验）
                    String nextMaterial = controlsSection.getString("next-page.material", "ARROW").toUpperCase();
                    publicGUISettings.put("next-page-material", nextMaterial);
                    publicGUISettings.put("next-page-name", controlsSection.getString("next-page.name", "&a下一页"));
                    int nextSlot = controlsSection.getInt("next-page.slot", 53);
                    nextSlot = Math.max(MIN_INVENTORY_SLOT, Math.min(MAX_INVENTORY_SLOT, nextSlot));
                    publicGUISettings.put("next-page-slot", nextSlot);
                }

                // 显示信息
                publicGUISettings.put("info-display", publicSection.getStringList("info-display"));
            }
        }
    }

    private void loadSweeperSettings() {
        try {
            ConfigurationSection sweeperSection = config.getConfigurationSection("sweeper");
            if (sweeperSection != null) {
                sweeperEnabled = sweeperSection.getBoolean("enabled", true);
                sweeperInterval = sweeperSection.getInt("interval", 300);

                // 确保扫地间隔合理
                if (sweeperInterval < 10) {
                    sweeperInterval = 300; // 默认5分钟
                    plugin.getLogger().warning("扫地间隔太小，已设置为默认值300秒");
                } else if (sweeperInterval > 3600) {
                    sweeperInterval = 3600; // 最大1小时
                    plugin.getLogger().warning("扫地间隔太大，已设置为3600秒");
                }

                // 黑名单材质名称规范化
                blacklist = new ArrayList<>();
                List<String> rawBlacklist = sweeperSection.getStringList("blacklist");
                for (String mat : rawBlacklist) {
                    blacklist.add(mat.toUpperCase().trim());
                }

                plugin.getLogger().info("扫地功能配置: enabled=" + sweeperEnabled + ", interval=" + sweeperInterval + "秒");
            } else {
                plugin.getLogger().info("使用扫地功能默认配置");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载扫地设置时出错: " + e.getMessage());
            sweeperEnabled = true;
            sweeperInterval = 300;
        }
    }

    private void loadPublicTrashSettings() {
        try {
            ConfigurationSection publicTrashSection = config.getConfigurationSection("public-trash");
            if (publicTrashSection != null) {
                autoRefresh = publicTrashSection.getInt("auto-refresh", 3600);
                autoRefresh = Math.max(0, autoRefresh); // 0表示禁用

                // 移除了 broadcastRefresh 相关代码
                plugin.getLogger().info("自动刷新配置: 间隔=" + autoRefresh + "秒");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载公共垃圾桶设置时出错: " + e.getMessage());
        }
    }

    private void loadPriceSettings() {
        ConfigurationSection pricesSection = config.getConfigurationSection("prices");
        if (pricesSection != null) {
            // 默认价格兜底（非负）
            defaultPrice = pricesSection.getDouble("default-price", 1.0);
            defaultPrice = Math.max(0.0, defaultPrice);

            ConfigurationSection customPricesSection = pricesSection.getConfigurationSection("custom-prices");
            if (customPricesSection != null) {
                Set<String> keys = customPricesSection.getKeys(false);
                for (String key : keys) {
                    // 材质名称规范化+价格兜底
                    double price = customPricesSection.getDouble(key, defaultPrice);
                    price = Math.max(0.0, price);
                    customPrices.put(key.toUpperCase().trim(), price);
                }
            }
        }
    }

    public void reload() {
        loadConfig();
    }

    // Getter方法（保持原有逻辑，无修改）
    public List<String> getEnabledWorlds() {
        return new ArrayList<>(enabledWorlds);
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    // GUI相关
    public String getPersonalTitle() {
        return (String) personalGUISettings.getOrDefault("title", "&8垃圾桶");
    }

    public int getPersonalRows() {
        return (int) personalGUISettings.getOrDefault("rows", 6);
    }

    public String getPersonalDecorationMaterial() {
        return (String) personalGUISettings.getOrDefault("decoration-material", "BLACK_STAINED_GLASS_PANE");
    }

    public String getPersonalDecorationName() {
        return (String) personalGUISettings.getOrDefault("decoration-name", "&8╏");
    }

    public String getPublicTitle() {
        return (String) publicGUISettings.getOrDefault("title", "&6公共垃圾桶");
    }

    public int getPublicRows() {
        return (int) publicGUISettings.getOrDefault("rows", 6);
    }

    public String getPublicDecorationMaterial() {
        return (String) publicGUISettings.getOrDefault("decoration-material", "GRAY_STAINED_GLASS_PANE");
    }

    public String getPublicDecorationName() {
        return (String) publicGUISettings.getOrDefault("decoration-name", "&8╏");
    }

    public int getPreviousPageSlot() {
        return (int) publicGUISettings.getOrDefault("previous-page-slot", 45);
    }

    public int getNextPageSlot() {
        return (int) publicGUISettings.getOrDefault("next-page-slot", 53);
    }

    public String getPreviousPageMaterial() {
        return (String) publicGUISettings.getOrDefault("previous-page-material", "ARROW");
    }

    public String getNextPageMaterial() {
        return (String) publicGUISettings.getOrDefault("next-page-material", "ARROW");
    }

    public String getPreviousPageName() {
        return (String) publicGUISettings.getOrDefault("previous-page-name", "&a上一页");
    }

    public String getNextPageName() {
        return (String) publicGUISettings.getOrDefault("next-page-name", "&a下一页");
    }

    public List<String> getInfoDisplay(TrashItem trashItem) {
        List<String> info = (List<String>) publicGUISettings.getOrDefault("info-display", new ArrayList<>());
        List<String> formattedInfo = new ArrayList<>();

        for (String line : info) {
            line = line.replace("%player%", trashItem.getOwner())
                    .replace("%time%", trashItem.getFormattedTime())
                    .replace("%price%", String.format("%.1f", trashItem.getPrice()))
                    .replace("%durability%", trashItem.getDurabilityString())
                    .replace("%item_id%", trashItem.getItemType().toString());
            formattedInfo.add(line);
        }

        return formattedInfo;
    }

    // 扫地功能
    public boolean isSweeperEnabled() {
        return sweeperEnabled;
    }

    public int getSweeperInterval() {
        return sweeperInterval;
    }

    public int getSweeperRadius() {
        return sweeperRadius;
    }

    public String getSweeperMessage() {
        return sweeperMessage;
    }

    public boolean isBroadcastSweep() {
        return broadcastSweep;
    }

    public List<String> getBlacklist() {
        return new ArrayList<>(blacklist);
    }

    public boolean isBlacklisted(Material material) {
        return material != null && blacklist.contains(material.toString().toUpperCase());
    }

    public double getPrice(Material material) {
        if (material == null) {
            return defaultPrice;
        }
        String materialName = material.name();
        return customPrices.getOrDefault(materialName, defaultPrice);
    }

    // 公共垃圾桶
    public int getAutoRefresh() {
        return autoRefresh;
    }

    public int getSaveInterval() {
        return saveInterval;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    // 价格
    public double getDefaultPrice() {
        return defaultPrice;
    }

    public void setCustomPrice(String material, double price) {
        // 新增：空指针校验+参数规范化
        if (config == null) {
            plugin.getLogger().warning("配置未初始化，无法保存自定义价格");
            return;
        }
        String normalizedMaterial = material.toUpperCase().trim();
        double normalizedPrice = Math.max(0.0, price);

        customPrices.put(normalizedMaterial, normalizedPrice);
        config.set("prices.custom-prices." + normalizedMaterial, normalizedPrice);

        try {
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().warning("保存自定义价格时出错：" + e.getMessage());
        }
    }

    public Map<String, Double> getCustomPrices() {
        return new HashMap<>(customPrices);
    }

    // 经济
    public boolean useVaultEconomy() {
        return useVault;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    // 语言
    public String getLanguage() {
        return language;
    }

    // 新增/补充的方法
    public FileConfiguration getConfig() {
        if (this.config == null) {
            plugin.reloadConfig();
            this.config = plugin.getConfig();
        }
        return this.config;
    }

    public List<String> getPersonalDecorationLore() {
        @SuppressWarnings("unchecked")
        List<String> lore = (List<String>) personalGUISettings.getOrDefault("decoration-lore", new ArrayList<>());
        return new ArrayList<>(lore);
    }

    public List<String> getPublicDecorationLore() {
        @SuppressWarnings("unchecked")
        List<String> lore = (List<String>) publicGUISettings.getOrDefault("decoration-lore", new ArrayList<>());
        return new ArrayList<>(lore);
    }
}