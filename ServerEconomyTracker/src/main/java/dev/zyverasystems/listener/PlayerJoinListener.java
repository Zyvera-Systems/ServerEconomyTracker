package dev.zyverasystems.listener;

import dev.zyverasystems.utils.EconomyTrackerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final EconomyTrackerService trackerService;

    public PlayerJoinListener(EconomyTrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        trackerService.handleFirstJoin(e.getPlayer());
    }

}
