package com.yei_bai.trashplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class EconomyManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private Economy economy = null;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // 通过 TrashPlugin 实例获取 ConfigManager
        this.config = TrashPlugin.getInstance().getConfigManager();
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    public boolean isEnabled() {
        // 由配置决定是否启用经济系统
        if (!config.useVaultEconomy()) {
            return false;
        }
        return economy != null;
    }

    public boolean isConfigEnabled() {
        return config.useVaultEconomy();
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (!isEnabled()) {
            return false;
        }

        try {
            return economy.has(player, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "检查玩家余额时出错", e);
            return false;
        }
    }

    public void takeMoney(Player player, double amount) {
        if (!isEnabled()) {
            return;
        }

        try {
            economy.withdrawPlayer(player, amount).transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "扣除玩家金币时出错", e);
        }
    }

    public double getBalance(Player player) {
        if (!isEnabled()) {
            return 0.0;
        }

        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取玩家余额时出错", e);
            return 0.0;
        }
    }

    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.1f", amount) + " " + config.getCurrencySymbol();
        }

        try {
            return economy.format(amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "格式化金额时出错", e);
            return String.format("%.1f", amount) + " " + config.getCurrencySymbol();
        }
    }

    public String getProviderName() {
        if (economy != null) {
            return economy.getName();
        }
        return "None";
    }
}