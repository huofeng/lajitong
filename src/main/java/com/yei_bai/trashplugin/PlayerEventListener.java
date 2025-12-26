package com.yei_bai.trashplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener implements Listener {

    private final SweeperManager sweeperManager;
    private final AutoRefreshManager autoRefreshManager;

    public PlayerEventListener(SweeperManager sweeperManager, AutoRefreshManager autoRefreshManager) {
        this.sweeperManager = sweeperManager;
        this.autoRefreshManager = autoRefreshManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (sweeperManager != null) {
            sweeperManager.playerJoined();
        }

        if (autoRefreshManager != null) {
            autoRefreshManager.playerJoined();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (sweeperManager != null) {
            sweeperManager.playerLeft();
        }

        if (autoRefreshManager != null) {
            autoRefreshManager.playerLeft();
        }
    }
}