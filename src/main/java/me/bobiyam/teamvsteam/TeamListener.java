package me.bobiyam.teamvsteam;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Map;

public class TeamListener implements Listener {

    private final TeamVsTeam plugin;

    public TeamListener(TeamVsTeam plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();

        Map<String, List<Player>> teams = plugin.getTeams();

        String damagedTeam = plugin.getPlayerTeam(damaged);
        String damagerTeam = plugin.getPlayerTeam(damager);

        // Ако са в един и същи отбор → блокирай
        if (damagedTeam != null && damagedTeam.equals(damagerTeam)) {
            event.setCancelled(true);
            damager.sendMessage(ChatColor.RED + "Не можеш да удряш съотборник!");
        }
    }
}