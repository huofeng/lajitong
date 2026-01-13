package com.yei_bai.trashplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PublicTrashGUI {

    private final TrashPlugin plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    private final DataManager dataManager;

    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public PublicTrashGUI() {
        this.plugin = TrashPlugin.getInstance();
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
        this.dataManager = plugin.getDataManager();
    }

    public void openGUI(Player player, int page) {
        openGUI(player, page, false);
    }

    public void openGUI(Player player, int page, boolean forceOpen) {
        // 检查世界是否启用
        if (!config.isWorldEnabled(player.getWorld().getName()) &&
                !player.hasPermission("trashplugin.bypass") && !forceOpen) {
            player.sendMessage(messages.getMessage("world-disabled"));
            return;
        }

        // 检查分页
        int itemsPerPage = config.getItemsPerPage();
        int totalPages = dataManager.getTotalPages(itemsPerPage);

        if (page < 0) {
            page = 0;
        }

        if (totalPages > 0 && page >= totalPages) {
            page = totalPages - 1;
        }

        // 保存玩家当前页码
        playerPages.put(player.getUniqueId(), page);

        // 创建GUI
        int rows = config.getPublicRows();
        int size = rows * 9;
        String baseTitle = ChatColor.translateAlternateColorCodes('&', config.getPublicTitle());
        String pageTitle = totalPages > 0 ?
                baseTitle + ChatColor.GRAY + " (" + (page + 1) + "/" + totalPages + ")" :
                baseTitle;

        Inventory gui = Bukkit.createInventory(null, size, pageTitle);

        // 填充装饰物品
        fillDecoration(gui);

        // 添加控制按钮
        addControlButtons(gui, page, totalPages);

        // 添加物品
        addItems(gui, page, itemsPerPage);

        // 打开GUI
        player.openInventory(gui);

        // 发送消息
        if (totalPages > 0) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("page", String.valueOf(page + 1));
            replacements.put("total", String.valueOf(totalPages));
            player.sendMessage(messages.getMessage("gui.public-opened", replacements));
        } else {
            player.sendMessage(messages.getMessage("gui.no-items"));
        }
    }

    private void fillDecoration(Inventory gui) {
        try {
            Material material = Material.valueOf(config.getPublicDecorationMaterial());
            ItemStack decoration = new ItemStack(material, 1);
            ItemMeta meta = decoration.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        config.getPublicDecorationName()));
                decoration.setItemMeta(meta);
            }

            // 只填充底部一行（第6行，索引45-53），不包括控制按钮位置
            for (int i = 45; i < 54; i++) {
                if (i != config.getPreviousPageSlot() &&
                        i != config.getNextPageSlot()) {
                    gui.setItem(i, decoration.clone());
                }
            }

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的公共垃圾桶装饰物品材质: " + config.getPublicDecorationMaterial());
            // 使用默认材质
            ItemStack defaultDecoration = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
            ItemMeta meta = defaultDecoration.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&8╏"));
            defaultDecoration.setItemMeta(meta);

            for (int i = 45; i < 54; i++) {
                if (i != config.getPreviousPageSlot() &&
                        i != config.getNextPageSlot()) {
                    gui.setItem(i, defaultDecoration.clone());
                }
            }
        }
    }

    private void addControlButtons(Inventory gui, int currentPage, int totalPages) {
        // 上一页按钮
        if (currentPage > 0) {
            try {
                Material prevMaterial = Material.valueOf(config.getPreviousPageMaterial());
                ItemStack prevButton = new ItemStack(prevMaterial, 1);
                ItemMeta prevMeta = prevButton.getItemMeta();
                prevMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        config.getPreviousPageName()));

                List<String> prevLore = new ArrayList<>();
                prevLore.add(ChatColor.GRAY + "点击切换到上一页");
                prevMeta.setLore(prevLore);
                prevButton.setItemMeta(prevMeta);

                gui.setItem(config.getPreviousPageSlot(), prevButton);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的上一页按钮材质: " + config.getPreviousPageMaterial());
            }
        }

        // 下一页按钮
        if (currentPage < totalPages - 1) {
            try {
                Material nextMaterial = Material.valueOf(config.getNextPageMaterial());
                ItemStack nextButton = new ItemStack(nextMaterial, 1);
                ItemMeta nextMeta = nextButton.getItemMeta();
                nextMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        config.getNextPageName()));

                List<String> nextLore = new ArrayList<>();
                nextLore.add(ChatColor.GRAY + "点击切换到下一页");
                nextMeta.setLore(nextLore);
                nextButton.setItemMeta(nextMeta);

                gui.setItem(config.getNextPageSlot(), nextButton);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的下一页按钮材质: " + config.getNextPageMaterial());
            }
        }

        // 移除购买按钮
    }

    private void addItems(Inventory gui, int page, int itemsPerPage) {
        List<TrashItem> items = dataManager.getPublicTrashItems(page, itemsPerPage);

        for (int i = 0; i < items.size(); i++) {
            if (i >= itemsPerPage) {
                break; // 超出每页限制
            }

            TrashItem trashItem = items.get(i);
            ItemStack displayItem = createDisplayItem(trashItem);
            gui.setItem(i, displayItem);
        }
    }

    private ItemStack createDisplayItem(TrashItem trashItem) {
        ItemStack originalItem = trashItem.getItemStack().clone();
        ItemMeta meta = originalItem.getItemMeta();

        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(originalItem.getType());
        }

        // 获取原始物品名称
        String itemName = meta.hasDisplayName() ?
                meta.getDisplayName() :
                formatItemName(originalItem.getType());

        // 设置显示名称
        meta.setDisplayName(ChatColor.RESET + itemName);

        // 添加详细信息到Lore
        List<String> lore = new ArrayList<>();

        // 原始物品的Lore
        if (meta.hasLore()) {
            lore.addAll(Objects.requireNonNull(meta.getLore()));
            lore.add("");
        }

        // 添加插件信息
        lore.add(ChatColor.GRAY + "来自: " + ChatColor.YELLOW + trashItem.getOwner());
        lore.add(ChatColor.GRAY + "丢弃时间: " + ChatColor.YELLOW + trashItem.getFormattedTime());

        // 计算价格
        double price = calculateItemPrice(trashItem);
        lore.add(ChatColor.GRAY + "价格: " + ChatColor.YELLOW +
                String.format("%.1f", price) + " " + config.getCurrencySymbol());

        // 添加购买提示
        lore.add("");
        lore.add(ChatColor.GREEN + "左键点击购买此物品");

        meta.setLore(lore);
        originalItem.setItemMeta(meta);

        return originalItem;
    }

    private String formatItemName(Material material) {
        String[] words = material.toString().toLowerCase().split("_");
        return getString(words);
    }

    @NotNull
    static String getString(String[] words) {
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    private double calculateItemPrice(TrashItem trashItem) {
        // 首先检查数据管理器中的自定义价格
        Double customPrice = dataManager.getCustomPrice(trashItem.getItemType().toString());
        if (customPrice != null) {
            return customPrice * trashItem.getItemStack().getAmount();
        }

        // 然后检查配置中的价格
        double price = config.getPrice(trashItem.getItemType());
        return price * trashItem.getItemStack().getAmount();
    }

}