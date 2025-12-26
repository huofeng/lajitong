package com.yei_bai.trashplugin;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicInteger;

public class AutoRefreshManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final DataManager dataManager;

    private ScheduledTask refreshTask = null;
    private BukkitRunnable bukkitRunnable = null;
    private boolean isRunning = false;
    private int playerCount = 0;
    private final boolean isFolia;
    private final AtomicInteger executionCount = new AtomicInteger(0);

    public AutoRefreshManager(JavaPlugin plugin, ConfigManager config, DataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;

        // 检测是否是Folia
        boolean foliaDetected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException e) {
            foliaDetected = false;
        }
        this.isFolia = foliaDetected;

        if (config.isDebug()) {
            plugin.getLogger().info("自动刷新管理器初始化完成，使用" + (isFolia ? "Folia" : "传统") + "调度器");
            plugin.getLogger().info("自动刷新间隔: " + config.getAutoRefresh() + "秒");
        }
    }

    /**
     * 增加在线玩家计数，尝试启动自动刷新任务
     */
    public synchronized void playerJoined() {
        playerCount++;

        if (config == null) {
            plugin.getLogger().warning("自动刷新管理器: 配置为null，无法启动自动刷新任务");
            return;
        }

        int autoRefresh = config.getAutoRefresh();
        if (playerCount > 0 && autoRefresh > 0 && !isRunning) {
            startRefreshTask();
        }
    }

    /**
     * 减少在线玩家计数，如果没有玩家则停止自动刷新任务
     */
    public synchronized void playerLeft() {
        playerCount--;

        if (playerCount <= 0 && isRunning) {
            if (config.isDebug()) {
                plugin.getLogger().info("无玩家在线，停止自动刷新任务");
            }
            stopRefreshTask();
        }
    }

    /**
     * 启动自动刷新任务
     */
    private void startRefreshTask() {
        if (isRunning) {
            return;
        }

        int interval = config.getAutoRefresh();
        if (interval <= 0) {
            return; // 自动刷新已禁用
        }

        try {
            if (isFolia) {
                startRefreshTaskFolia(interval);
            } else {
                startRefreshTaskBukkit(interval);
            }

            isRunning = true;
            if (config.isDebug()) {
                plugin.getLogger().info("公共垃圾桶自动刷新已启动，间隔: " + interval + "秒，当前在线玩家: " + playerCount);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("启动自动刷新任务时出错: " + e.getMessage());
            isRunning = false;
        }
    }

    /**
     * 为Folia服务器启动自动刷新任务
     */
    private void startRefreshTaskFolia(int interval) {
        try {
            long intervalTicks = interval * 20L; // 转换为tick

            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();

            // 启动重复任务
            refreshTask = scheduler.runAtFixedRate(plugin, task -> {
                int count = executionCount.incrementAndGet();
                if (config.isDebug()) {
                    plugin.getLogger().info("Folia自动刷新任务触发 [#" + count + "]");
                }
                executeRefreshTask();
            }, intervalTicks, intervalTicks);

        } catch (Exception e) {
            plugin.getLogger().warning("Folia自动刷新调度器启动失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 为传统Bukkit服务器启动自动刷新任务
     */
    private void startRefreshTaskBukkit(int interval) {
        try {
            int intervalTicks = interval * 20;

            bukkitRunnable = new BukkitRunnable() {
                @Override
                public void run() {
                    int count = executionCount.incrementAndGet();
                    if (config.isDebug()) {
                        plugin.getLogger().info("Bukkit自动刷新任务触发 [#" + count + "]");
                    }
                    executeRefreshTask();
                }
            };

            bukkitRunnable.runTaskTimer(plugin, intervalTicks, intervalTicks);

        } catch (Exception e) {
            plugin.getLogger().warning("Bukkit自动刷新调度器启动失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 停止自动刷新任务
     */
    private void stopRefreshTask() {
        if (refreshTask != null) {
            try {
                refreshTask.cancel();
                if (config.isDebug()) {
                    plugin.getLogger().info("Folia自动刷新任务已取消");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("停止自动刷新任务时出错: " + e.getMessage());
            } finally {
                refreshTask = null;
            }
        } else if (bukkitRunnable != null) {
            try {
                bukkitRunnable.cancel();
                if (config.isDebug()) {
                    plugin.getLogger().info("Bukkit自动刷新任务已取消");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("停止自动刷新任务时出错: " + e.getMessage());
            } finally {
                bukkitRunnable = null;
            }
        }
        isRunning = false;
    }

    /**
     * 执行自动刷新任务
     */
    private void executeRefreshTask() {
        int interval = config.getAutoRefresh();
        if (interval <= 0 || !isRunning) {
            return;
        }

        int itemCount = dataManager.getPublicTrashSize();
        if (itemCount > 0) {
            dataManager.clearPublicTrash();
            plugin.getLogger().info("公共垃圾桶自动刷新，清除了 " + itemCount + " 个物品");

            // 发送广播消息
            sendBroadcastMessage(itemCount);
        } else if (config.isDebug()) {
            plugin.getLogger().info("公共垃圾桶为空，无需刷新");
        }
    }

    /**
     * 发送广播消息
     */
    private void sendBroadcastMessage(int itemCount) {
        try {
            // 构造广播消息
            String broadcastMessage = "§7公共垃圾桶已自动刷新，清除了 §e" + itemCount + " §7个物品";

            // 在全局调度器中广播消息
            if (isFolia) {
                GlobalRegionScheduler globalScheduler = Bukkit.getGlobalRegionScheduler();
                globalScheduler.run(plugin, task -> Bukkit.broadcastMessage(broadcastMessage));
            } else {
                // 传统Bukkit服务器，直接广播
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(broadcastMessage));
            }

        } catch (Exception e) {
            plugin.getLogger().warning("发送广播消息时出错: " + e.getMessage());
        }
    }

    /**
     * 停止所有任务
     */
    public void stop() {
        stopRefreshTask();
        playerCount = 0;
    }

    /**
     * 检查自动刷新任务是否在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 立即执行一次刷新任务
     */
    public void refreshNow() {
        int interval = config.getAutoRefresh();
        if (interval > 0) {
            executeRefreshTask();
        }
    }
}