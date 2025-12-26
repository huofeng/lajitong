package com.yei_bai.trashplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ALL")
public class Commands implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        TrashPlugin plugin = TrashPlugin.getInstance();
        if (plugin == null) {
            sender.sendMessage(ChatColor.RED + "插件未正确初始化");
            return true;
        }

        ConfigManager config = plugin.getConfigManager();
        MessageManager messages = plugin.getMessageManager();
        DataManager dataManager = plugin.getDataManager();
        PublicTrashGUI publicTrashGUI = plugin.getPublicTrashGUI();

        String cmd = command.getName().toLowerCase();

        return switch (cmd) {
            case "trash" -> handleTrashCommand(sender, args, config, messages);
            case "publictrash" -> handlePublicTrashCommand(sender, args, config, messages, dataManager, publicTrashGUI);
            case "trashadmin" -> handleAdminCommand(sender, args, plugin, config, messages, dataManager);
            default -> false;
        };
    }

    private boolean handleTrashCommand(CommandSender sender, String[] args, ConfigManager config, MessageManager messages) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能执行此命令！");
            return true;
        }

        if (!player.hasPermission("trashplugin.use")) {
            player.sendMessage("§c你没有权限使用个人垃圾桶！");
            return true;
        }

        // 打开个人垃圾桶
        PersonalTrashGUI personalTrashGUI = new PersonalTrashGUI();
        personalTrashGUI.openGUI(player);
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean handlePublicTrashCommand(CommandSender sender, String[] args, ConfigManager config, MessageManager messages, DataManager dataManager, PublicTrashGUI publicTrashGUI) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能执行此命令！");
            return true;
        }

        if (!player.hasPermission("trashplugin.public")) {
            player.sendMessage("§c你没有权限使用公共垃圾桶！");
            return true;
        }

        int page = 0;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]) - 1; // 用户输入从1开始
                if (page < 0) page = 0;
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "无效的页码！");
                return true;
            }
        }

        publicTrashGUI.openGUI(player, page);
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args, TrashPlugin plugin, ConfigManager config, MessageManager messages, DataManager dataManager) {
        if (!sender.hasPermission("trashplugin.admin")) {
            sender.sendMessage("§c你没有管理员权限！");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "reload":
                return handleReloadCommand(sender, config, messages, dataManager, plugin);

            case "setprice":
                return handleSetPriceCommand(sender, args, config, messages);

            case "sweeper":
                return handleSweeperCommand(sender, args, plugin, messages);

            case "refresh":
                return handleRefreshCommand(sender, args, plugin, messages);

            case "clear":
                return handleClearCommand(sender, args, dataManager, messages);

            case "econ":
                return handleEconomyStatusCommand(sender, plugin);

            case "test":
                return handleTestCommand(sender, args, plugin);

            case "help":
                sendAdminHelp(sender);
                return true;

            default:
                sender.sendMessage("§c未知命令！使用 /trashadmin help 查看帮助");
                return true;
        }
    }

    private boolean handleReloadCommand(CommandSender sender, ConfigManager config, MessageManager messages, DataManager dataManager, TrashPlugin plugin) {
        config.reload();
        messages.reload();
        dataManager.reload();

        // 核心新增：重载配置后重置扫地倒计时
        if (plugin.getSweeperManager() != null) {
            plugin.resetSweeperCountdown();
            sender.sendMessage("§a配置已重新加载，扫地倒计时已重置");
        } else {
            sender.sendMessage("§a配置已重新加载");
        }

        plugin.getLogger().info("配置文件已重新加载 - " + sender.getName() + "（扫地倒计时已重置）");
        return true;
    }

    private boolean handleSetPriceCommand(CommandSender sender, String[] args, ConfigManager config, MessageManager messages) {
        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /trashadmin setprice <物品ID> <价格>");
            return false;
        }

        String materialName = args[1].toUpperCase();
        double price;

        try {
            price = Double.parseDouble(args[2]);
            if (price < 0) {
                sender.sendMessage("§c价格不能为负数！");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c价格必须是数字！");
            return true;
        }

        // 验证物品ID
        try {
            Material material = Material.valueOf(materialName);
            config.setCustomPrice(materialName, price);

            sender.sendMessage("§a已设置 " + materialName + " 的价格为: " + price);

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的物品ID: " + materialName);
        }

        return true;
    }

    private boolean handleSweeperCommand(CommandSender sender, String[] args, TrashPlugin plugin, MessageManager messages) {
        SweeperManager sweeperManager = plugin.getSweeperManager();

        if (args.length < 2) {
            // 显示扫地功能状态和倒计时
            String status = sweeperManager != null && sweeperManager.isRunning() ?
                    "§a运行中" : "§c已停止";
            sender.sendMessage("§6扫地功能状态: " + status);

            // 显示倒计时（优化：未运行时提示）
            if (sweeperManager != null) {
                if (sweeperManager.isRunning()) {
                    long remainingSeconds = sweeperManager.getRemainingTime();
                    sender.sendMessage("§6下次扫地时间: " + formatTime(remainingSeconds));
                } else {
                    sender.sendMessage("§6下次扫地时间: §c扫地功能未运行");
                }
            }

            sender.sendMessage("§7使用 /trashadmin sweeper debug 查看详细信息");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "status":
                String status = sweeperManager != null && sweeperManager.isRunning() ?
                        "§a运行中" : "§c已停止";
                sender.sendMessage("§6扫地功能状态: " + status);

                // 显示倒计时（优化：未运行时提示）
                if (sweeperManager != null) {
                    if (sweeperManager.isRunning()) {
                        long remainingSeconds = sweeperManager.getRemainingTime();
                        sender.sendMessage("§6下次扫地时间: " + formatTime(remainingSeconds));
                    } else {
                        sender.sendMessage("§6下次扫地时间: §c扫地功能未运行");
                    }
                }
                return true;

            case "now":
                if (sweeperManager != null) {
                    sweeperManager.sweepNow();
                    sender.sendMessage("§a已执行一次扫地任务，倒计时已重置");
                } else {
                    sender.sendMessage("§c扫地管理器未初始化");
                }
                return true;

            case "debug":
                if (sweeperManager != null) {
                    sender.sendMessage("§6=== 扫地功能调试信息 ===");
                    sender.sendMessage("§7是否运行: " + (sweeperManager.isRunning() ? "§a是" : "§c否"));
                    sender.sendMessage("§7配置启用: " + (plugin.getConfigManager().isSweeperEnabled() ? "§a是" : "§c否"));
                    sender.sendMessage("§7扫地间隔: " + plugin.getConfigManager().getSweeperInterval() + "秒");

                    // 显示倒计时（优化：未运行时提示）
                    if (sweeperManager.isRunning()) {
                        long remainingSeconds = sweeperManager.getRemainingTime();
                        sender.sendMessage("§7下次扫地时间: " + formatTime(remainingSeconds));
                    } else {
                        sender.sendMessage("§7下次扫地时间: §c扫地功能未运行");
                    }

                    // 测试立即执行一次
                    sender.sendMessage("§7正在测试扫地功能...");
                    sweeperManager.sweepNow();
                    sender.sendMessage("§a测试完成，倒计时已重置，请查看控制台日志");
                } else {
                    sender.sendMessage("§c扫地管理器未初始化");
                }
                return true;

            default:
                sender.sendMessage("§c未知操作！可用操作: status, now, debug");
                return true;
        }
    }

    // 格式化时间（秒 -> 时:分:秒）- 优化：支持0秒显示
    private String formatTime(long seconds) {
        if (seconds <= 0) return "§e立即执行";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("§e%d时%d分%d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("§e%d分%d秒", minutes, secs);
        } else {
            return String.format("§e%d秒", secs);
        }
    }

    private boolean handleRefreshCommand(CommandSender sender, String[] args, TrashPlugin plugin, MessageManager messages) {
        if (args.length < 2) {
            // 显示自动刷新状态
            AutoRefreshManager refreshManager = plugin.getAutoRefreshManager();
            String status = refreshManager != null && refreshManager.isRunning() ?
                    "§a运行中" : "§c已停止";
            sender.sendMessage("§6自动刷新状态: " + status);
            sender.sendMessage("§7自动刷新间隔: " + plugin.getConfigManager().getAutoRefresh() + "秒");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "status":
                AutoRefreshManager refreshManager = plugin.getAutoRefreshManager();
                String status = refreshManager != null && refreshManager.isRunning() ?
                        "§a运行中" : "§c已停止";
                sender.sendMessage("§6自动刷新状态: " + status);
                sender.sendMessage("§7自动刷新间隔: " + plugin.getConfigManager().getAutoRefresh() + "秒");
                return true;

            case "now":
                refreshManager = plugin.getAutoRefreshManager();
                if (refreshManager != null) {
                    refreshManager.refreshNow();
                    sender.sendMessage("§a已执行一次公共垃圾桶刷新");
                } else {
                    sender.sendMessage("§c自动刷新管理器未初始化");
                }
                return true;

            default:
                sender.sendMessage("§c未知操作！可用操作: status, now");
                return true;
        }
    }

    private boolean handleClearCommand(CommandSender sender, String[] args, DataManager dataManager, MessageManager messages) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§c警告：这将清空公共垃圾桶中的所有物品！");
            sender.sendMessage("§c使用 /trashadmin clear confirm 确认操作");
            return true;
        }

        int itemCount = dataManager.getPublicTrashSize();
        dataManager.clearPublicTrash();

        sender.sendMessage("§a已清空公共垃圾桶，移除了 " + itemCount + " 个物品");
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§6=== 垃圾桶插件管理命令 ===");
        sender.sendMessage("§e/trashadmin reload §7- 重载插件配置（重置扫地倒计时）");
        sender.sendMessage("§e/trashadmin setprice <物品ID> <价格> §7- 设置物品价格");
        sender.sendMessage("§e/trashadmin sweeper status §7- 查看扫地功能状态及倒计时");
        sender.sendMessage("§e/trashadmin sweeper now §7- 立即执行一次扫地任务（重置倒计时）");
        sender.sendMessage("§e/trashadmin sweeper debug §7- 调试扫地功能（含倒计时）");
        sender.sendMessage("§e/trashadmin refresh status §7- 查看自动刷新状态");
        sender.sendMessage("§e/trashadmin refresh now §7- 立即执行一次自动刷新");
        sender.sendMessage("§e/trashadmin clear confirm §7- 清空公共垃圾桶");
        sender.sendMessage("§e/trashadmin econ §7- 检查经济系统状态");
        sender.sendMessage("§e/trashadmin test <类型> §7- 测试功能 (sweeper|refresh|scheduler)");
        sender.sendMessage("§e/trashadmin help §7- 显示此帮助");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        TrashPlugin plugin = TrashPlugin.getInstance();
        if (plugin == null) {
            return Collections.emptyList();
        }

        ConfigManager config = plugin.getConfigManager();
        DataManager dataManager = plugin.getDataManager();

        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("trashadmin")) {
            if (args.length == 1) {
                commands.add("reload");
                commands.add("setprice");
                commands.add("sweeper");
                commands.add("refresh");
                commands.add("clear");
                commands.add("econ");
                commands.add("test");
                commands.add("help");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("sweeper")) {
                commands.add("status");
                commands.add("now");
                commands.add("debug");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("refresh")) {
                commands.add("status");
                commands.add("now");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("setprice")) {
                // 提供物品ID补全
                for (Material material : Material.values()) {
                    if (!material.isLegacy()) {
                        commands.add(material.toString().toLowerCase());
                    }
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
                commands.add("confirm");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
                commands.add("sweeper");
                commands.add("refresh");
                commands.add("scheduler");
            }
        } else if (command.getName().equalsIgnoreCase("publictrash")) {
            if (args.length == 1) {
                int totalPages = dataManager.getTotalPages(config.getItemsPerPage());
                for (int i = 1; i <= Math.min(totalPages, 10); i++) {
                    commands.add(String.valueOf(i));
                }
            }
        }

        org.bukkit.util.StringUtil.copyPartialMatches(args[args.length - 1], commands, completions);
        Collections.sort(completions);
        return completions;
    }

    private boolean handleEconomyStatusCommand(CommandSender sender, TrashPlugin plugin) {
        if (!sender.hasPermission("trashplugin.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }

        EconomyManager economyManager = plugin.getEconomyManager();

        sender.sendMessage("§6=== 经济系统状态 ===");
        sender.sendMessage("§e配置启用: " +
                (economyManager.isConfigEnabled() ? "§a是" : "§c否"));

        if (economyManager.isEnabled()) {
            sender.sendMessage("§a状态: 已连接");
            sender.sendMessage("§e提供者: " + economyManager.getProviderName());

            if (sender instanceof Player) {
                Player player = (Player) sender;
                double balance = economyManager.getBalance(player);
                sender.sendMessage("§e您的余额: " + economyManager.format(balance));
            }
        } else if (economyManager.isConfigEnabled()) {
            sender.sendMessage("§e状态: 配置已启用但未连接到经济系统");
        } else {
            sender.sendMessage("§c状态: 已在配置中禁用");
        }

        return true;
    }

    private boolean handleTestCommand(CommandSender sender, String[] args, TrashPlugin plugin) {
        if (!sender.hasPermission("trashplugin.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /trashadmin test <sweeper|refresh|scheduler>");
            return true;
        }

        String testType = args[1].toLowerCase();

        switch (testType) {
            case "sweeper":
                return testSweeper(sender, plugin);
            case "refresh":
                return testRefresh(sender, plugin);
            case "scheduler":
                return testScheduler(sender, plugin);
            default:
                sender.sendMessage("§c未知测试类型: sweeper, refresh, scheduler");
                return true;
        }
    }

    private boolean testSweeper(CommandSender sender, TrashPlugin plugin) {
        sender.sendMessage("§6=== 扫地功能测试 ===");

        SweeperManager sweeper = plugin.getSweeperManager();
        ConfigManager config = plugin.getConfigManager();

        sender.sendMessage("§7扫地功能启用: " + (config.isSweeperEnabled() ? "§a是" : "§c否"));
        sender.sendMessage("§7扫地间隔: " + config.getSweeperInterval() + "秒");
        sender.sendMessage("§7运行状态: " + (sweeper.isRunning() ? "§a运行中" : "§c已停止"));

        // 显示倒计时（优化：未运行时提示）
        if (sweeper != null) {
            if (sweeper.isRunning()) {
                long remainingSeconds = sweeper.getRemainingTime();
                sender.sendMessage("§7下次扫地时间: " + formatTime(remainingSeconds));
            } else {
                sender.sendMessage("§7下次扫地时间: §c扫地功能未运行");
            }
        }

        // 检查启用世界
        List<String> enabledWorlds = config.getEnabledWorlds();
        sender.sendMessage("§7启用世界: " + String.join(", ", enabledWorlds));

        // 检查黑名单
        List<String> blacklist = config.getBlacklist();
        sender.sendMessage("§7黑名单物品数: " + blacklist.size());

        // 手动测试扫地
        if (sweeper != null) {
            sender.sendMessage("§7正在执行扫地测试...");
            sweeper.sweepNow();
            sender.sendMessage("§a扫地测试已执行，倒计时已重置，请查看控制台日志");
        } else {
            sender.sendMessage("§c扫地管理器未初始化");
        }

        return true;
    }

    private boolean testRefresh(CommandSender sender, TrashPlugin plugin) {
        sender.sendMessage("§6=== 自动刷新测试 ===");

        AutoRefreshManager refresh = plugin.getAutoRefreshManager();
        ConfigManager config = plugin.getConfigManager();

        sender.sendMessage("§7自动刷新间隔: " + config.getAutoRefresh() + "秒");
        sender.sendMessage("§7运行状态: " + (refresh.isRunning() ? "§a运行中" : "§c已停止"));

        // 获取当前公共垃圾桶物品数量
        int itemCount = plugin.getDataManager().getPublicTrashSize();
        sender.sendMessage("§7当前公共垃圾桶物品数: " + itemCount);

        // 测试手动刷新
        if (refresh != null) {
            sender.sendMessage("§7正在执行自动刷新测试...");
            refresh.refreshNow();
            sender.sendMessage("§a自动刷新测试已执行，请查看控制台日志");
        } else {
            sender.sendMessage("§c自动刷新管理器未初始化");
        }

        return true;
    }

    private boolean testScheduler(CommandSender sender, TrashPlugin plugin) {
        sender.sendMessage("§6=== 调度器测试 ===");

        // 检测是否是Folia
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        sender.sendMessage("§7服务器类型: " + (isFolia ? "§aFolia" : "§e传统Bukkit"));
        sender.sendMessage("§7调度器类: " + Bukkit.getScheduler().getClass().getName());

        // 测试调度器是否工作
        if (isFolia) {
            try {
                io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
                if (scheduler != null) {
                    scheduler.run(plugin, task -> {
                        plugin.getLogger().info("调度器测试任务执行成功");
                    });
                    sender.sendMessage("§aFolia调度器测试已发送");
                } else {
                    sender.sendMessage("§cFolia全局调度器为空");
                }
            } catch (Exception e) {
                sender.sendMessage("§c调度器测试失败: " + e.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("传统调度器测试任务执行成功");
            });
            sender.sendMessage("§a传统调度器测试已发送");
        }

        return true;
    }
}