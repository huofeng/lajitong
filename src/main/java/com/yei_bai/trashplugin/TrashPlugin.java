package com.yei_bai.trashplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Level;
import java.io.File;

public class TrashPlugin extends JavaPlugin {

    private static TrashPlugin instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DataManager dataManager;
    private EconomyManager economyManager;
    private PublicTrashGUI publicTrashGUI;
    private SweeperManager sweeperManager;
    private AutoRefreshManager autoRefreshManager;

    private boolean isEconomyInitialized = false;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("========================================");
        getLogger().info(" 垃圾桶插件 v" + getDescription().getVersion() + " 启动中...");
        getLogger().info("========================================");

        try {
            // 检测服务器类型
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                getLogger().info("检测到Folia服务器，启用Folia兼容模式");
            } catch (ClassNotFoundException e) {
                getLogger().info("传统Bukkit服务器，使用标准调度器");
            }

            getLogger().info("服务器类型: " + Bukkit.getServer().getName());

            // 1. 创建数据文件夹
            if (!getDataFolder().exists()) {
                if (getDataFolder().mkdirs()) {
                    getLogger().info("✓ 已创建数据文件夹: " + getDataFolder().getPath());
                }
            }

            // 2. 复制默认配置文件
            copyDefaultConfigFiles();

            // 3. 初始化核心管理器
            getLogger().info("初始化配置管理器...");
            configManager = new ConfigManager(this);
            configManager.loadConfig();

            getLogger().info("初始化消息管理器...");
            messageManager = new MessageManager(this);
            messageManager.loadMessages();

            getLogger().info("初始化数据管理器...");
            dataManager = new DataManager(this);
            dataManager.loadData();

            getLogger().info("初始化经济管理器...");
            economyManager = new EconomyManager(this);

            getLogger().info("初始化GUI...");
            publicTrashGUI = new PublicTrashGUI();

            getLogger().info("初始化扫地管理器（含倒计时）...");
            sweeperManager = new SweeperManager(this, configManager, dataManager);

            getLogger().info("初始化自动刷新管理器...");
            autoRefreshManager = new AutoRefreshManager(this, configManager, dataManager);

            // 4. 设置语言环境
            messageManager.setLanguage(configManager.getLanguage());

            // 5. 注册事件监听器
            getLogger().info("注册事件监听器...");
            PluginManager pm = getServer().getPluginManager();

            // GUI监听器
            GUIListener guiListener = new GUIListener();
            pm.registerEvents(guiListener, this);

            // 玩家事件监听器（联动倒计时启停）
            PlayerEventListener playerEventListener = new PlayerEventListener(sweeperManager, autoRefreshManager);
            pm.registerEvents(playerEventListener, this);

            // 6. 注册命令
            getLogger().info("注册命令...");
            Commands commands = new Commands();
            Objects.requireNonNull(getCommand("trash")).setExecutor(commands);
            Objects.requireNonNull(getCommand("publictrash")).setExecutor(commands);
            Objects.requireNonNull(getCommand("trashadmin")).setExecutor(commands);

            getLogger().info("✓ 核心系统初始化完成（含扫地倒计时功能）");

            // 7. 初始化经济系统（不涉及调度器）
            initializeEconomy();

            getLogger().info("========================================");
            getLogger().info(" 插件启动成功！");
            getLogger().info(" 功能: 个人垃圾桶, 公共垃圾桶, 扫地功能(带倒计时), 物品回购");
            getLogger().info("========================================");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "插件启动失败", e);
            getLogger().severe("详细错误: " + e.getMessage());

            // 尝试安全关闭插件
            try {
                Bukkit.getPluginManager().disablePlugin(this);
            } catch (Exception ex) {
                getLogger().severe("无法安全禁用插件: " + ex.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("正在安全关闭插件...");

        // 停止扫地管理器（含倒计时任务）
        if (sweeperManager != null) {
            sweeperManager.stop();
            getLogger().info("✓ 扫地倒计时任务已停止");
        }

        // 停止自动刷新管理器
        if (autoRefreshManager != null) {
            autoRefreshManager.stop();
        }

        // 保存数据
        if (dataManager != null) {
            try {
                dataManager.saveData();
                getLogger().info("✓ 插件数据已保存");
            } catch (Exception e) {
                getLogger().warning("保存数据时出错: " + e.getMessage());
            }
        }

        getLogger().info("========================================");
        getLogger().info(" 垃圾桶插件已安全关闭");
        getLogger().info("========================================");
    }

    // 新增：对外提供重置扫地倒计时的方法（供Commands调用）
    public void resetSweeperCountdown() {
        if (sweeperManager != null) {
            sweeperManager.resetCountdown();
            getLogger().info("扫地倒计时已重置");
        }
    }

    private void copyDefaultConfigFiles() {
        try {
            // 复制 config.yml
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                getLogger().info("创建默认配置文件 config.yml...");
                try (InputStream in = getResource("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("✓ 已创建 config.yml");
                    } else {
                        getLogger().warning("无法找到内置的 config.yml 文件");
                        configFile.createNewFile();
                    }
                }
            } else {
                getLogger().info("✓ 配置文件已存在: config.yml");
            }

            // 复制 messages.yml
            File messagesFile = new File(getDataFolder(), "messages.yml");
            if (!messagesFile.exists()) {
                getLogger().info("创建默认消息文件 messages.yml...");
                try (InputStream in = getResource("messages.yml")) {
                    if (in != null) {
                        Files.copy(in, messagesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("✓ 已创建 messages.yml");
                    } else {
                        getLogger().warning("无法找到内置的 messages.yml 文件");
                        messagesFile.createNewFile();
                    }
                }
            } else {
                getLogger().info("✓ 消息文件已存在: messages.yml");
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "复制默认配置文件时出错", e);
        }
    }

    private void initializeEconomy() {
        if (!configManager.useVaultEconomy()) {
            getLogger().info("经济系统功能已在配置中禁用");
            return;
        }

        if (isEconomyInitialized) {
            return;
        }

        getLogger().info("正在连接经济系统...");

        try {
            // 检查Vault插件是否安装
            if (getServer().getPluginManager().getPlugin("Vault") == null) {
                getLogger().warning("✗ Vault插件未安装，经济系统功能将不可用");
                getLogger().info("如需使用经济功能，请安装Vault插件");
                return;
            }

            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                Economy economy = rsp.getProvider();
                economyManager.setEconomy(economy);
                getLogger().info("✓ 经济系统连接成功: " + economy.getName());
            } else {
                getLogger().warning("✗ 未找到可用的经济服务提供者");
                getLogger().info("请确保已安装支持的经济插件（如EssentialsX、CMI等）");
            }
        } catch (Exception e) {
            getLogger().warning("连接经济系统时发生错误: " + e.getMessage());
        } finally {
            isEconomyInitialized = true;
        }
    }

    public static TrashPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PublicTrashGUI getPublicTrashGUI() {
        return publicTrashGUI;
    }

    public SweeperManager getSweeperManager() {
        return sweeperManager;
    }

    public AutoRefreshManager getAutoRefreshManager() {
        return autoRefreshManager;
    }

}