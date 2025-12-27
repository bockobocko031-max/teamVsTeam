package me.bobiyam.teamvsteam;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;

public class TeamListener implements Listener {

    private final TeamVsTeam plugin;

    public TeamListener(TeamVsTeam plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Премахване от опашката
        plugin.getQueue().remove(player);
        plugin.removeFromQueueDatabase(player);

        // Премахване от отбора
        String team = plugin.getPlayerTeam(player);
        if (team != null) {
            plugin.getTeams().get(team).remove(player);
            plugin.removeFromTeamDatabase(team, player);
            // Изпращане на съобщение на всички
            plugin.getServer().broadcastMessage(
                    plugin.getMessage("player.left-team")
                            .replace("{player}", player.getName())
                            .replace("{team}", team)
            );
        }
    }
}