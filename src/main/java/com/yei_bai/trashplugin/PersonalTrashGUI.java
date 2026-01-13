package com.yei_bai.trashplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class PersonalTrashGUI {

    private final ConfigManager config;
    private final MessageManager messages;

    public PersonalTrashGUI() {
        TrashPlugin plugin = TrashPlugin.getInstance();
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
    }

    public void openGUI(Player player) {
        // 检查世界是否启用
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            player.sendMessage(messages.getMessage("world-disabled"));
            return;
        }

        // 创建GUI
        int rows = config.getPersonalRows();
        int size = rows * 9;
        String title = ChatColor.translateAlternateColorCodes('&', config.getPersonalTitle());

        Inventory gui = Bukkit.createInventory(null, size, title);

        // 不再填充装饰物品，GUI初始为空
        // 玩家可以自由放入物品

        // 打开GUI
        player.openInventory(gui);
        player.sendMessage(messages.getMessage("gui.personal-opened"));
    }
}