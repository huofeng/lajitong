package com.yei_bai.trashplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
public class GUIListener implements Listener {

    private final TrashPlugin plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    private final DataManager dataManager;
    private final EconomyManager economyManager;

    public GUIListener() {
        this.plugin = TrashPlugin.getInstance();
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
        this.dataManager = plugin.getDataManager();
        this.economyManager = plugin.getEconomyManager();
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryView view = event.getView();
        String title = ChatColor.stripColor(view.getTitle());

        // 检查是否为个人垃圾桶
        String personalTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                config.getPersonalTitle()));

        if (title.equals(personalTitle)) {
            handlePersonalTrashClick(event, player);
            return;
        }

        // 检查是否为公共垃圾桶
        String publicTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                config.getPublicTitle()));

        if (title.startsWith(publicTitle)) {
            handlePublicTrashClick(event, player, view);
        }
    }

    private void handlePersonalTrashClick(InventoryClickEvent event, Player player) {
        // 个人垃圾桶允许所有操作
        // 玩家可以自由放入、取出、移动物品
        // 只在关闭时转移物品到公共垃圾桶

        // 不阻止任何点击操作
        // 允许玩家自由操作
    }

    private void handlePublicTrashClick(InventoryClickEvent event, Player player, InventoryView view) {
        int slot = event.getRawSlot();

        // 检查是否点击了控制按钮
        if (slot == config.getPreviousPageSlot()) {
            // 上一页按钮
            event.setCancelled(true);
            handlePreviousPageClick(player, view);
            return;
        } else if (slot == config.getNextPageSlot()) {
            // 下一页按钮
            event.setCancelled(true);
            handleNextPageClick(player, view);
            return;
        }

        // 移除购买按钮的逻辑，现在直接点击物品购买
        // 检查是否点击了装饰物品
        if (event.getCurrentItem() != null) {
            Material decorationMaterial = Material.valueOf(config.getPublicDecorationMaterial());
            if (event.getCurrentItem().getType() == decorationMaterial) {
                event.setCancelled(true);
                return;
            }
        }

        // 检查是否在物品区域
        int itemsPerPage = config.getItemsPerPage();
        int startSlot = 0;
        int endSlot = itemsPerPage - 1;

        if (slot >= startSlot && slot <= endSlot) {
            // 在物品区域点击，直接购买
            event.setCancelled(true);
            handleDirectBuy(player, view, slot);
        }
    }

    private void handlePreviousPageClick(Player player, InventoryView view) {
        // 获取当前页码
        int currentPage = getCurrentPage(view);
        if (currentPage <= 0) {
            player.sendMessage(messages.getMessage("gui.already-first-page"));
            return;
        }

        // 打开上一页
        PublicTrashGUI publicTrashGUI = plugin.getPublicTrashGUI();
        publicTrashGUI.openGUI(player, currentPage - 1);
    }

    private void handleNextPageClick(Player player, InventoryView view) {
        // 获取当前页码
        int currentPage = getCurrentPage(view);
        int totalPages = dataManager.getTotalPages(config.getItemsPerPage());

        if (currentPage >= totalPages - 1) {
            player.sendMessage(messages.getMessage("gui.already-last-page"));
            return;
        }

        // 打开下一页
        PublicTrashGUI publicTrashGUI = plugin.getPublicTrashGUI();
        publicTrashGUI.openGUI(player, currentPage + 1);
    }

    private void handleDirectBuy(Player player, InventoryView view, int slot) {
        // 获取选中的物品索引
        int page = getCurrentPage(view);
        int itemIndex = slot + (page * config.getItemsPerPage());
        List<TrashItem> allItems = dataManager.getPublicTrashItems();

        if (itemIndex < 0 || itemIndex >= allItems.size()) {
            player.sendMessage(messages.getMessage("item.invalid-item"));
            return;
        }

        TrashItem trashItem = allItems.get(itemIndex);

        // 检查经济系统
        if (!economyManager.isEnabled()) {
            player.sendMessage(messages.getMessage("economy.not-available"));
            return;
        }

        // 检查玩家金币
        double price = trashItem.getPrice();
        if (!economyManager.hasEnoughMoney(player, price)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("price", String.format("%.1f", price));
            player.sendMessage(messages.getMessage("item.buy-no-money", replacements));
            return;
        }

        // 检查背包空间
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(messages.getMessage("item.buy-inventory-full"));
            return;
        }

        // 扣款
        economyManager.takeMoney(player, price);

        // 给玩家物品
        ItemStack item = trashItem.getItemStack().clone();
        player.getInventory().addItem(item);

        // 从公共垃圾桶移除
        dataManager.removePublicTrashItem(itemIndex);

        // 发送消息
        Map<String, String> replacements = new HashMap<>();
        replacements.put("price", String.format("%.1f", price));
        player.sendMessage(messages.getMessage("item.buy-success", replacements));

        // 刷新GUI
        PublicTrashGUI publicTrashGUI = plugin.getPublicTrashGUI();
        publicTrashGUI.openGUI(player, page);
    }

    @SuppressWarnings("deprecation")
    private int getCurrentPage(InventoryView view) {
        // 从GUI标题获取页码
        String title = view.getTitle();
        String pageText = ChatColor.stripColor(title);

        if (pageText.contains("(") && pageText.contains(")")) {
            try {
                int start = pageText.indexOf("(") + 1;
                int end = pageText.indexOf(")");
                String pageStr = pageText.substring(start, end).split("/")[0].trim();
                return Integer.parseInt(pageStr) - 1;
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        InventoryView view = event.getView();
        String title = ChatColor.stripColor(view.getTitle());

        // 检查是否为垃圾桶GUI
        String personalTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                config.getPersonalTitle()));
        String publicTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                config.getPublicTitle()));

        if (title.equals(personalTitle) || title.startsWith(publicTitle)) {
            // 个人垃圾桶允许拖拽
            if (title.equals(personalTitle)) {
                return; // 允许所有拖拽操作
            }

            // 公共垃圾桶检查是否拖拽到装饰物品上
            for (int slot : event.getRawSlots()) {
                if (slot < view.getTopInventory().getSize()) {
                    ItemStack item = view.getTopInventory().getItem(slot);
                    if (item != null) {
                        Material decorationMaterial = Material.valueOf(config.getPublicDecorationMaterial());
                        if (item.getType() == decorationMaterial) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryView view = event.getView();
        String title = ChatColor.stripColor(view.getTitle());

        // 检查是否为个人垃圾桶
        String personalTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                config.getPersonalTitle()));

        if (title.equals(personalTitle)) {
            Inventory inventory = view.getTopInventory();

            // 将所有物品转移到公共垃圾桶
            int transferredCount = 0;
            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    // 添加到公共垃圾桶
                    dataManager.addPublicTrashItem(item, player.getName());
                    transferredCount++;
                }
            }

            // 清空个人垃圾桶
            inventory.clear();

            if (transferredCount > 0) {
                player.sendMessage("§7已将 " + transferredCount + " 个物品转移到公共垃圾桶");
            }
        }
    }
}