package com.yei_bai.trashplugin;

import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@SerializableAs("TrashItem")
public class TrashItem implements ConfigurationSerializable {

    private final ItemStack itemStack;
    private final String owner;
    private Date discardTime;
    private double customPrice = -1; // -1表示使用默认价格

    public TrashItem(ItemStack itemStack, String owner, Date discardTime) {
        this.itemStack = itemStack.clone();
        this.owner = owner;
        this.discardTime = discardTime;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("item", itemStack.serialize()); // 序列化 ItemStack 为 Map
        map.put("owner", owner);
        map.put("discardTime", discardTime.getTime()); // 存储时间戳
        if (customPrice >= 0) {
            map.put("customPrice", customPrice);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public TrashItem(Map<String, Object> map) {
        // 处理旧版数据兼容
        Object itemObj = map.get("item");
        if (itemObj instanceof Map) {
            this.itemStack = ItemStack.deserialize((Map<String, Object>) itemObj);
        } else if (itemObj instanceof ItemStack) {
            this.itemStack = (ItemStack) itemObj;
        } else {
            this.itemStack = new ItemStack(Material.AIR);
        }

        this.owner = (String) map.getOrDefault("owner", "Unknown");

        // 处理 discardTime
        Object timeObj = map.get("discardTime");
        if (timeObj instanceof Long) {
            this.discardTime = new Date((Long) timeObj);
        } else if (timeObj instanceof String) {
            try {
                this.discardTime = new Date(Long.parseLong((String) timeObj));
            } catch (NumberFormatException e) {
                this.discardTime = new Date(); // 使用当前时间
            }
        } else {
            this.discardTime = new Date(); // 使用当前时间
        }

        if (map.containsKey("customPrice")) {
            Object priceObj = map.get("customPrice");
            if (priceObj instanceof Double) {
                this.customPrice = (Double) priceObj;
            } else if (priceObj instanceof Integer) {
                this.customPrice = ((Integer) priceObj).doubleValue();
            }
        }
    }

    public static TrashItem deserialize(Map<String, Object> map) {
        return new TrashItem(map);
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public Material getItemType() {
        return itemStack.getType();
    }

    public String getOwner() {
        return owner;
    }

    public String getFormattedTime() {
        return TrashPlugin.getInstance().getDataManager().formatDate(discardTime);
    }

    public double getPrice() {
        if (customPrice >= 0) {
            return customPrice;
        }

        ConfigManager config = TrashPlugin.getInstance().getConfigManager();
        return config.getPrice(itemStack.getType()) * itemStack.getAmount();
    }

    public String getDurabilityString() {
        if (itemStack.getType().getMaxDurability() > 0) {
            int durability = itemStack.getType().getMaxDurability() - itemStack.getDurability();
            return durability + "/" + itemStack.getType().getMaxDurability();
        }
        return "N/A";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrashItem trashItem = (TrashItem) o;
        return Objects.equals(itemStack, trashItem.itemStack) &&
                Objects.equals(owner, trashItem.owner) &&
                Objects.equals(discardTime, trashItem.discardTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemStack, owner, discardTime);
    }
}