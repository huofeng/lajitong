package com.yei_bai.trashplugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final JavaPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;
    private final Map<String, String> messageCache = new HashMap<>();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // 加载内置的默认消息
        loadDefaultMessages();

        // 缓存所有消息
        cacheMessages();
    }

    private void loadDefaultMessages() {
        // 中文消息
        if (messages.getString("language", "zh_CN").equals("zh_CN")) {
            setDefaultMessagesZH();
        } else {
            setDefaultMessagesEN();
        }

        // 保存消息文件
        try {
            messages.save(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().warning("无法保存消息文件: " + e.getMessage());
        }
    }

    private void setDefaultMessagesZH() {
        // 通用消息
        messages.addDefault("prefix", "&8[&6垃圾桶&8] &7");
        messages.addDefault("no-permission", "&c你没有权限使用此命令！");
        messages.addDefault("player-only", "&c只有玩家可以执行此命令！");
        messages.addDefault("world-disabled", "&c此世界已禁用垃圾桶功能！");
        messages.addDefault("config-reloaded", "&a配置文件已重新加载！");
        messages.addDefault("error-occurred", "&c发生错误: %error%");

        // 命令消息
        messages.addDefault("command.usage", "&7用法: &e/trashadmin <reload|setprice|sweeper>");
        messages.addDefault("command.reload-success", "&a配置文件已重新加载！");
        messages.addDefault("command.setprice-usage", "&7用法: &e/trashadmin setprice <物品ID> <价格>");
        messages.addDefault("command.setprice-success", "&a已设置物品 &e%item% &a的价格为 &e%price% &a金币");
        messages.addDefault("command.setprice-invalid-item", "&c无效的物品ID: &e%item%");
        messages.addDefault("command.setprice-invalid-price", "&c无效的价格格式！");
        messages.addDefault("command.sweeper-status", "&7扫地功能状态: &e%status%");
        messages.addDefault("command.sweeper-enabled", "&a已启用");
        messages.addDefault("command.sweeper-disabled", "&c已禁用");

        // GUI消息
        messages.addDefault("gui.personal-opened", "&7已打开个人垃圾桶");
        messages.addDefault("gui.public-opened", "&7已打开公共垃圾桶，第 &e%page% &7页，共 &e%total% &7页");
        messages.addDefault("gui.no-items", "&7公共垃圾桶是空的");
        messages.addDefault("gui.page-not-exist", "&c该页面不存在！");
        messages.addDefault("gui.already-first-page", "&c已经是第一页了！");
        messages.addDefault("gui.already-last-page", "&c已经是最后一页了！");

        // 物品消息
        messages.addDefault("item.added-to-public", "&7已将物品添加到公共垃圾桶");
        messages.addDefault("item.removed-from-public", "&7已将物品从公共垃圾桶移除");
        messages.addDefault("item.public-trash-full", "&c公共垃圾桶已满！");
        messages.addDefault("item.buy-success", "&a成功购买物品，花费 &e%price% &a金币");
        messages.addDefault("item.buy-no-money", "&c金币不足！需要 &e%price% &c金币");
        messages.addDefault("item.buy-inventory-full", "&c背包已满，无法购买物品！");
        messages.addDefault("item.sell-success", "&a成功出售物品，获得 &e%price% &a金币");
        messages.addDefault("item.invalid-item", "&c无效的物品！");

        // 扫地功能消息
        messages.addDefault("sweeper.started", "&a扫地功能已启动，间隔: &e%interval% &a秒");
        messages.addDefault("sweeper.stopped", "&c扫地功能已停止");
        messages.addDefault("sweeper.cleaning", "&7正在清扫地面物品...");
        messages.addDefault("sweeper.items-cleaned", "&7已清扫 &e%count% &7个物品到公共垃圾桶");
        messages.addDefault("sweeper.broadcast", "&7地面上的垃圾已被自动清扫到公共垃圾桶，共清理了 &e%count% &7个物品");

        // 经济消息
        messages.addDefault("economy.not-available", "&c经济系统不可用，物品回购功能禁用！");
        messages.addDefault("economy.setup-failed", "&c经济系统初始化失败！");

        messages.options().copyDefaults(true);
    }

    private void setDefaultMessagesEN() {
        // 英文消息
        messages.addDefault("prefix", "&8[&6Trash&8] &7");
        messages.addDefault("no-permission", "&cYou don't have permission to use this command!");
        messages.addDefault("player-only", "&cOnly players can use this command!");
        messages.addDefault("world-disabled", "&cTrash system is disabled in this world!");
        messages.addDefault("config-reloaded", "&aConfiguration reloaded!");
        messages.addDefault("error-occurred", "&cAn error occurred: %error%");

        // 命令消息
        messages.addDefault("command.usage", "&7Usage: &e/trashadmin <reload|setprice|sweeper>");
        messages.addDefault("command.reload-success", "&aConfiguration reloaded!");
        messages.addDefault("command.setprice-usage", "&7Usage: &e/trashadmin setprice <itemID> <price>");
        messages.addDefault("command.setprice-success", "&aSet price of &e%item% &ato &e%price% coins");
        messages.addDefault("command.setprice-invalid-item", "&cInvalid item ID: &e%item%");
        messages.addDefault("command.setprice-invalid-price", "&cInvalid price format!");
        messages.addDefault("command.sweeper-status", "&7Sweeper status: &e%status%");
        messages.addDefault("command.sweeper-enabled", "&aEnabled");
        messages.addDefault("command.sweeper-disabled", "&cDisabled");

        // GUI消息
        messages.addDefault("gui.personal-opened", "&7Opened personal trash");
        messages.addDefault("gui.public-opened", "&7Opened public trash, page &e%page% &7of &e%total%");
        messages.addDefault("gui.no-items", "&7Public trash is empty");
        messages.addDefault("gui.page-not-exist", "&cThis page doesn't exist!");
        messages.addDefault("gui.already-first-page", "&cThis is already the first page!");
        messages.addDefault("gui.already-last-page", "&cThis is already the last page!");

        // 物品消息
        messages.addDefault("item.added-to-public", "&7Item added to public trash");
        messages.addDefault("item.removed-from-public", "&7Item removed from public trash");
        messages.addDefault("item.public-trash-full", "&cPublic trash is full!");
        messages.addDefault("item.buy-success", "&aSuccessfully bought item for &e%price% coins");
        messages.addDefault("item.buy-no-money", "&cNot enough coins! Need &e%price% coins");
        messages.addDefault("item.buy-inventory-full", "&cInventory is full, cannot buy item!");
        messages.addDefault("item.sell-success", "&aSuccessfully sold item for &e%price% coins");
        messages.addDefault("item.invalid-item", "&cInvalid item!");

        // 扫地功能消息
        messages.addDefault("sweeper.started", "&aSweeper started, interval: &e%interval% &aseconds");
        messages.addDefault("sweeper.stopped", "&cSweeper stopped");
        messages.addDefault("sweeper.cleaning", "&7Cleaning ground items...");
        messages.addDefault("sweeper.items-cleaned", "&7Cleaned &e%count% &7items to public trash");
        messages.addDefault("sweeper.broadcast", "&7Ground items have been cleaned to public trash");

        // 经济消息
        messages.addDefault("economy.not-available", "&cEconomy system not available, buyback disabled!");
        messages.addDefault("economy.setup-failed", "&cEconomy system setup failed!");

        messages.options().copyDefaults(true);
    }

    private void cacheMessages() {
        for (String key : messages.getKeys(true)) {
            if (messages.isString(key)) {
                messageCache.put(key, messages.getString(key));
            }
        }
    }

    public String getMessage(String key) {
        String message = messageCache.get(key);
        if (message == null) {
            return "&cMessage not found: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return message;
    }

    public void reload() {
        loadMessages();
    }

    public void setLanguage(String language) {
        messages.set("language", language);
        try {
            messages.save(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().warning("无法保存语言设置: " + e.getMessage());
        }
        reload();
    }
}