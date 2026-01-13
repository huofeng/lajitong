package com.yei_bai.trashplugin;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SweeperManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final DataManager dataManager;

    private ScheduledTask sweeperTask = null;
    private BukkitRunnable bukkitRunnable = null;
    private boolean isRunning = false;
    private int playerCount = 0;
    private final boolean isFolia;
    private final AtomicInteger executionCount = new AtomicInteger(0);

    // 用于统计本次清扫的物品数量
    private final AtomicInteger currentSweepCount = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<ItemStack> itemsToProcess = new ConcurrentLinkedQueue<>();
    private final AtomicLong sweepStartTime = new AtomicLong(0);

    // 新增：倒计时相关变量
    private int remainingTime; // 剩余秒数
    private ScheduledTask countdownTaskFolia = null; // Folia倒计时任务
    private BukkitRunnable countdownTaskBukkit = null; // Bukkit倒计时任务
    private boolean hasSentTenSecondWarning = false; // 标记是否已发送10秒警告

    public SweeperManager(JavaPlugin plugin, ConfigManager config, DataManager dataManager) {
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

        // 初始化剩余时间
        this.remainingTime = config.getSweeperInterval();

        if (config.isDebug()) {
            plugin.getLogger().info("扫地管理器初始化完成，使用" + (isFolia ? "Folia" : "传统") + "调度器");
        }
    }

    /**
     * 增加在线玩家计数，尝试启动扫地任务
     */
    public synchronized void playerJoined() {
        playerCount++;

        if (config == null) {
            plugin.getLogger().warning("扫地管理器: 配置为null，无法启动扫地任务");
            return;
        }

        if (config.isDebug()) {
            plugin.getLogger().info("玩家加入，当前玩家数: " + playerCount);
        }

        if (playerCount > 0 && config.isSweeperEnabled() && !isRunning) {
            if (config.isDebug()) {
                plugin.getLogger().info("尝试启动扫地任务...");
            }
            startSweeperTask();
            startCountdownTask(); // 新增：启动倒计时
        } else if (config.isDebug()) {
            plugin.getLogger().info("扫地功能状态: 启用=" + config.isSweeperEnabled() + ", 运行中=" + isRunning);
        }
    }

    /**
     * 减少在线玩家计数，如果没有玩家则停止扫地任务
     */
    public synchronized void playerLeft() {
        playerCount--;

        if (config.isDebug()) {
            plugin.getLogger().info("玩家退出，当前玩家数: " + playerCount);
        }

        if (playerCount <= 0 && isRunning) {
            plugin.getLogger().info("无玩家在线，停止扫地任务");
            stopSweeperTask();
            stopCountdownTask(); // 新增：停止倒计时
        }
    }

    /**
     * 启动扫地任务
     */
    private void startSweeperTask() {
        if (isRunning) {
            if (config.isDebug()) {
                plugin.getLogger().warning("扫地任务已在运行，跳过启动");
            }
            return;
        }

        int interval = config.getSweeperInterval();
        if (interval <= 0) {
            interval = 60;
        }

        // 重置倒计时
        this.remainingTime = interval;
        this.hasSentTenSecondWarning = false; // 重置警告标记

        if (config.isDebug()) {
            plugin.getLogger().info("开始启动扫地任务，间隔: " + interval + "秒");
        }

        try {
            if (isFolia) {
                startSweeperTaskFolia(interval);
            } else {
                startSweeperTaskBukkit(interval);
            }

            isRunning = true;
            plugin.getLogger().info("扫地功能已启动，间隔: " + interval + "秒，当前在线玩家: " + playerCount);

        } catch (Exception e) {
            plugin.getLogger().warning("启动扫地任务时出错: " + e.getMessage());
            isRunning = false;
        }
    }

    /**
     * 新增：启动倒计时任务
     */
    private void startCountdownTask() {
        // 先停止现有倒计时任务
        stopCountdownTask();

        int interval = config.getSweeperInterval();
        if (interval <= 0) {
            interval = 60;
        }
        remainingTime = interval;
        hasSentTenSecondWarning = false; // 重置警告标记

        if (isFolia) {
            // Folia倒计时（每秒执行一次）
            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
            countdownTaskFolia = scheduler.runAtFixedRate(plugin, task -> {
                remainingTime--;

                // 当剩余10秒且未发送过警告时，发送游戏内提示
                if (remainingTime == 10 && !hasSentTenSecondWarning) {
                    sendSweepWarningMessage();
                    hasSentTenSecondWarning = true;
                }

                if (remainingTime <= 0) {
                    remainingTime = config.getSweeperInterval(); // 重置
                    hasSentTenSecondWarning = false; // 重置警告标记
                }
            }, 20L, 20L); // 20ticks = 1秒
        } else {
            // Bukkit倒计时（每秒执行一次）
            countdownTaskBukkit = new BukkitRunnable() {
                @Override
                public void run() {
                    remainingTime--;

                    // 当剩余10秒且未发送过警告时，发送游戏内提示
                    if (remainingTime == 10 && !hasSentTenSecondWarning) {
                        sendSweepWarningMessage();
                        hasSentTenSecondWarning = true;
                    }

                    if (remainingTime <= 0) {
                        remainingTime = config.getSweeperInterval(); // 重置
                        hasSentTenSecondWarning = false; // 重置警告标记
                    }
                }
            };
            countdownTaskBukkit.runTaskTimer(plugin, 20L, 20L);
        }

        if (config.isDebug()) {
            plugin.getLogger().info("倒计时任务已启动，初始剩余时间: " + remainingTime + "秒");
        }
    }

    /**
     * 发送扫地前10秒警告消息
     */
    private void sendSweepWarningMessage() {
        String warningMessage = ChatColor.YELLOW + "[扫地系统] " + ChatColor.RED + "警告：地面物品清扫将在10秒后执行，请及时拾取重要物品！";

        try {
            if (isFolia) {
                // Folia需要在全局调度器中执行广播
                GlobalRegionScheduler globalScheduler = Bukkit.getGlobalRegionScheduler();
                globalScheduler.run(plugin, task -> Bukkit.broadcastMessage(warningMessage));
            } else {
                // 传统Bukkit直接广播
                Bukkit.broadcastMessage(warningMessage);
            }

            if (config.isDebug()) {
                plugin.getLogger().info("已发送扫地前10秒警告消息给所有玩家");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("发送扫地警告消息时出错: " + e.getMessage());
        }
    }

    /**
     * 为Folia服务器启动扫地任务
     */
    private void startSweeperTaskFolia(int interval) {
        try {
            long intervalTicks = interval * 20L; // 转换为tick

            if (config.isDebug()) {
                plugin.getLogger().info("使用Folia调度器，间隔ticks: " + intervalTicks);
            }

            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();

            // 启动重复任务
            sweeperTask = scheduler.runAtFixedRate(plugin, task -> {
                int count = executionCount.incrementAndGet();
                if (config.isDebug()) {
                    plugin.getLogger().info("Folia调度器触发扫地任务 [#" + count + "]");
                }

                // 执行清扫后重置倒计时和警告标记
                executeSweeperTaskFolia();
                remainingTime = config.getSweeperInterval();
                hasSentTenSecondWarning = false;

            }, intervalTicks, intervalTicks);

        } catch (Exception e) {
            plugin.getLogger().warning("Folia调度器启动失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 为传统Bukkit服务器启动扫地任务
     */
    private void startSweeperTaskBukkit(int interval) {
        try {
            int intervalTicks = interval * 20;

            if (config.isDebug()) {
                plugin.getLogger().info("使用传统Bukkit调度器，间隔ticks: " + intervalTicks);
            }

            bukkitRunnable = new BukkitRunnable() {
                @Override
                public void run() {
                    int count = executionCount.incrementAndGet();
                    if (config.isDebug()) {
                        plugin.getLogger().info("Bukkit调度器触发扫地任务 [#" + count + "]");
                    }
                    executeSweeperTaskBukkit();
                    remainingTime = config.getSweeperInterval(); // 执行清扫后重置倒计时
                    hasSentTenSecondWarning = false; // 重置警告标记
                }
            };

            bukkitRunnable.runTaskTimer(plugin, intervalTicks, intervalTicks);

        } catch (Exception e) {
            plugin.getLogger().warning("Bukkit调度器启动失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 停止扫地任务
     */
    private void stopSweeperTask() {
        if (sweeperTask != null) {
            try {
                sweeperTask.cancel();
                if (config.isDebug()) {
                    plugin.getLogger().info("Folia扫地任务已取消");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("停止扫地任务时出错: " + e.getMessage());
            } finally {
                sweeperTask = null;
            }
        } else if (bukkitRunnable != null) {
            try {
                bukkitRunnable.cancel();
                if (config.isDebug()) {
                    plugin.getLogger().info("Bukkit扫地任务已取消");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("停止扫地任务时出错: " + e.getMessage());
            } finally {
                bukkitRunnable = null;
            }
        }
        isRunning = false;
    }

    /**
     * 新增：停止倒计时任务
     */
    private void stopCountdownTask() {
        if (countdownTaskFolia != null) {
            try {
                countdownTaskFolia.cancel();
                if (config.isDebug()) {
                    plugin.getLogger().info("Folia倒计时任务已取消");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("停止Folia倒计时任务时出错: " + e.getMessage());
            } finally {
                countdownTaskFolia = null;
            }
        }
        if (countdownTaskBukkit != null) {
            try {
                countdownTaskBukkit.cancel();
                if (config.isDebug()) {
                    plugin.getLogger().info("Bukkit倒计时任务已取消");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("停止Bukkit倒计时任务时出错: " + e.getMessage());
            } finally {
                countdownTaskBukkit = null;
            }
        }
    }

    /**
     * 在Folia服务器中执行扫地任务（修复线程安全+编译错误）
     */
    private void executeSweeperTaskFolia() {
        sweepStartTime.set(System.currentTimeMillis());
        currentSweepCount.set(0);
        itemsToProcess.clear();

        if (config.isDebug()) {
            plugin.getLogger().info("=== 开始执行扫地任务 (Folia) ===");
        }

        if (!config.isSweeperEnabled()) {
            if (config.isDebug()) {
                plugin.getLogger().info("扫地功能已禁用，跳过执行");
            }
            return;
        }

        // 用于统计处理区块数
        AtomicInteger processedChunks = new AtomicInteger(0);

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            boolean worldEnabled = config.isWorldEnabled(worldName);

            if (!worldEnabled) {
                if (config.isDebug()) {
                    plugin.getLogger().info("世界 " + worldName + " 未启用扫地功能，跳过");
                }
                continue;
            }

            if (config.isDebug()) {
                plugin.getLogger().info("在世界 " + worldName + " 中查找掉落物...");
            }

            // 获取所有已加载的区块
            Chunk[] loadedChunks = world.getLoadedChunks();
            if (config.isDebug()) {
                plugin.getLogger().info("已加载区块数: " + loadedChunks.length);
            }

            for (Chunk chunk : loadedChunks) {
                processedChunks.incrementAndGet();

                // 获取区块中心位置用于区域调度
                int centerX = chunk.getX() * 16 + 8;
                int centerZ = chunk.getZ() * 16 + 8;
                Location center = new Location(world, centerX, 64, centerZ);

                // 为每个区块安排一个区域任务（区块所属线程）
                RegionScheduler regionScheduler = Bukkit.getRegionScheduler();
                regionScheduler.run(plugin, world, center.getBlockX(), center.getBlockZ(), regionTask -> {
                    try {
                        // 1. 区块线程中安全获取实体列表
                        Entity[] entities = chunk.getEntities();

                        // 2. 遍历实体，将每个实体的操作调度到「实体自身所属线程」
                        for (Entity entity : entities) {
                            // 跳过空实体
                            if (entity == null || entity.getLocation().getWorld() == null) {
                                continue;
                            }

                            // 核心修复：补充第三个参数null，解决编译错误 + 线程安全
                            entity.getScheduler().run(plugin, entityTask -> {
                                try {
                                    // 3. 此时在实体所属线程，安全操作实体
                                    if (entity instanceof Item itemEntity) {
                                        ItemStack itemStack = itemEntity.getItemStack();

                                        // 检查物品是否有效
                                        if (itemStack.getType() == org.bukkit.Material.AIR || itemStack.getAmount() <= 0) {
                                            return;
                                        }

                                        // 检查黑名单
                                        if (config.isBlacklisted(itemStack.getType())) {
                                            return;
                                        }

                                        // 添加到待处理列表（线程安全队列）
                                        itemsToProcess.offer(itemStack.clone());
                                        currentSweepCount.incrementAndGet();

                                        if (config.isDebug()) {
                                            plugin.getLogger().info("找到可清理物品: " + itemStack.getType() + " x" + itemStack.getAmount());
                                        }

                                        // 安全移除实体（在实体所属线程执行）
                                        itemEntity.remove();
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("处理实体时出错: " + e.getMessage());
                                }
                            }, null); // 关键：补充第三个参数null，解决编译错误
                        }

                    } catch (Exception e) {
                        plugin.getLogger().warning("处理区块时出错: " + e.getMessage());
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "处理区块时发生异常", e);
                    }
                });
            }
        }

        if (config.isDebug()) {
            plugin.getLogger().info("已安排 " + processedChunks.get() + " 个区块的处理任务");
        }

        // 等待一小段时间让任务完成，然后处理收集到的物品
        if (isFolia) {
            GlobalRegionScheduler globalScheduler = Bukkit.getGlobalRegionScheduler();
            globalScheduler.runDelayed(plugin, task -> processCollectedItems(), 20L); // 延迟1秒(20 ticks)处理
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::processCollectedItems, 20L);
        }
    }

    /**
     * 处理收集到的物品
     */
    private void processCollectedItems() {
        int totalCleaned = currentSweepCount.get();

        if (totalCleaned > 0) {
            long duration = System.currentTimeMillis() - sweepStartTime.get();

            // 将收集到的物品添加到公共垃圾桶
            int addedCount = 0;
            while (!itemsToProcess.isEmpty()) {
                ItemStack item = itemsToProcess.poll();
                if (item != null) {
                    dataManager.addPublicTrashItem(item, "扫地系统");
                    addedCount++;
                }
            }

            // 记录清理结果
            plugin.getLogger().info("扫地功能完成，共清理了 " + totalCleaned + " 个物品，其中 " + addedCount + " 个已添加到公共垃圾桶，耗时 " + duration + "ms");

            // 发送广播消息
            sendBroadcastMessage(totalCleaned);
        } else if (config.isDebug()) {
            plugin.getLogger().info("本次扫地未找到可清理的物品");
        }
    }

    /**
     * 在传统Bukkit服务器中执行扫地任务
     */
    private void executeSweeperTaskBukkit() {
        sweepStartTime.set(System.currentTimeMillis());
        currentSweepCount.set(0);
        itemsToProcess.clear();

        if (config.isDebug()) {
            plugin.getLogger().info("=== 开始执行扫地任务 (Bukkit) ===");
        }

        if (!config.isSweeperEnabled()) {
            if (config.isDebug()) {
                plugin.getLogger().info("扫地功能已禁用，跳过执行");
            }
            return;
        }

        int cleanedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            boolean worldEnabled = config.isWorldEnabled(worldName);

            if (!worldEnabled) {
                if (config.isDebug()) {
                    plugin.getLogger().info("世界 " + worldName + " 未启用扫地功能，跳过");
                }
                continue;
            }

            if (config.isDebug()) {
                plugin.getLogger().info("在世界 " + worldName + " 中查找掉落物...");
            }

            // 获取所有掉落物
            List<Entity> entities = world.getEntities();
            List<Item> itemsToRemove = new ArrayList<>();

            for (Entity entity : entities) {
                if (entity instanceof Item itemEntity) {
                    ItemStack itemStack = itemEntity.getItemStack();

                    // 检查物品是否有效
                    if (itemStack.getType() == org.bukkit.Material.AIR || itemStack.getAmount() <= 0) {
                        continue;
                    }

                    // 检查黑名单
                    if (config.isBlacklisted(itemStack.getType())) {
                        continue;
                    }

                    // 添加到待处理列表
                    itemsToProcess.offer(itemStack.clone());
                    itemsToRemove.add(itemEntity);
                    cleanedCount++;

                    if (config.isDebug()) {
                        plugin.getLogger().info("找到可清理物品: " + itemStack.getType() + " x" + itemStack.getAmount());
                    }
                }
            }

            if (config.isDebug()) {
                plugin.getLogger().info("在世界 " + worldName + " 中找到 " + itemsToRemove.size() + " 个可清理物品");
            }

            // 移除实体
            for (Item item : itemsToRemove) {
                try {
                    item.remove();
                } catch (Exception e) {
                    plugin.getLogger().warning("移除物品实体时出错: " + e.getMessage());
                }
            }
        }

        currentSweepCount.set(cleanedCount);

        // 延迟处理收集到的物品
        Bukkit.getScheduler().runTaskLater(plugin, this::processCollectedItems, 1L);
    }

    /**
     * 发送广播消息
     */
    private void sendBroadcastMessage(int cleanedCount) {
        try {
            // 构造广播消息
            String broadcastMessage = "§7地面上的垃圾已被自动清扫到公共垃圾桶，共清理了 §e" + cleanedCount + " §7个物品";

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
        stopSweeperTask();
        stopCountdownTask(); // 新增：停止倒计时
        playerCount = 0;
    }

    /**
     * 检查扫地任务是否在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 立即执行一次扫地任务
     */
    public void sweepNow() {
        if (config.isDebug()) {
            plugin.getLogger().info("手动执行扫地任务");
        }
        if (config.isSweeperEnabled()) {
            if (isFolia) {
                executeSweeperTaskFolia();
            } else {
                executeSweeperTaskBukkit();
            }
            // 手动执行后重置倒计时和警告标记
            remainingTime = config.getSweeperInterval();
            hasSentTenSecondWarning = false;
        } else {
            plugin.getLogger().warning("扫地功能已禁用，无法执行");
        }
    }

    // 新增：获取剩余时间（秒）
    public int getRemainingTime() {
        return remainingTime;
    }

    // 新增：配置重载时重置倒计时
    public void resetCountdown() {
        int newInterval = config.getSweeperInterval();
        this.remainingTime = newInterval;
        this.hasSentTenSecondWarning = false; // 重置警告标记
        if (config.isDebug()) {
            plugin.getLogger().info("配置重载，倒计时已重置为: " + newInterval + "秒");
        }
        // 重启倒计时任务以应用新间隔
        if (isRunning) {
            startCountdownTask();
        }
    }
}