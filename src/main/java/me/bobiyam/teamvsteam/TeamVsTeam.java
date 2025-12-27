package me.bobiyam.teamvsteam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class TeamVsTeam extends JavaPlugin {

    private final List<Player> queue = new ArrayList<>();
    private final Map<String, List<Player>> teams = new LinkedHashMap<>();
    private List<String> teamNames;
    private List<ChatColor> teamColors;

    @Override
    public void onEnable() {
        // Зареждане на config.yml
        saveDefaultConfig();
        reloadConfig();

        // Зареждане на имена и цветове на отбори
        teamNames = getConfig().getStringList("teams.default-names");
        List<String> colors = getConfig().getStringList("teams.default-colors");
        teamColors = new ArrayList<>();
        for (String color : colors) {
            try {
                teamColors.add(ChatColor.valueOf(color.toUpperCase()));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Невалиден цвят в config.yml: " + color);
            }
        }

        getLogger().info("TeamVsTeam plugin е активиран!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TeamVsTeam plugin е деактивиран!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Тези команди могат да се използват само от играчи.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Невалидна команда. Използвайте /team help.");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "join":
                if (!player.hasPermission("teamvsteam.join")) {
                    player.sendMessage(getMessage("errors.no-permission"));
                    return true;
                }
                handleJoin(player);
                break;

            case "create":
                if (!player.hasPermission("teamvsteam.create")) {
                    player.sendMessage(getMessage("errors.admin-only"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Моля, въведете брой отбори: /team create <number>");
                    return true;
                }
                try {
                    int numTeams = Integer.parseInt(args[1]);
                    handleCreateTeams(numTeams);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Невалидно число!");
                }
                break;

            case "match":
                if (!player.hasPermission("teamvsteam.match")) {
                    player.sendMessage(getMessage("errors.admin-only"));
                    return true;
                }
                handleStartMatch();
                break;

            default:
                player.sendMessage(ChatColor.RED + "Невалидна подкоманда.");
        }

        return true;
    }

    private void handleJoin(Player player) {
        if (queue.contains(player)) {
            player.sendMessage(getMessage("queue.already-in-queue"));
            return;
        }
        queue.add(player);
        player.sendMessage(getMessage("queue.join-success"));
    }

    private void handleCreateTeams(int numTeams) {
        if (queue.isEmpty()) {
            Bukkit.getServer().broadcastMessage(getMessage("errors.not-enough-players"));
            return;
        }

        teams.clear();

        // Инициализация на отборите
        for (int i = 0; i < numTeams; i++) {
            String teamName = i < teamNames.size() ? teamNames.get(i) : "Team" + (i + 1);
            teams.put(teamName, new ArrayList<>());
        }

        // Разбъркване на играчите за случайност
        List<Player> shuffledPlayers = new ArrayList<>(queue);
        Collections.shuffle(shuffledPlayers);

        // Изчисляваме колко играчи на отбор
        int totalPlayers = shuffledPlayers.size();
        int basePlayersPerTeam = totalPlayers / numTeams; // минимален брой на отбор
        int extraPlayers = totalPlayers % numTeams;       // оставащи играчи

        Iterator<Player> it = shuffledPlayers.iterator();
        List<String> teamKeys = new ArrayList<>(teams.keySet());

        for (int i = 0; i < numTeams; i++) {
            int playersInThisTeam = basePlayersPerTeam + (i < extraPlayers ? 1 : 0);
            List<Player> currentTeam = teams.get(teamKeys.get(i));

            for (int j = 0; j < playersInThisTeam && it.hasNext(); j++) {
                currentTeam.add(it.next());
            }
        }

        // Изпращане на съобщения на играчите
        int colorIndex = 0;
        for (String teamName : teams.keySet()) {
            ChatColor color = teamColors.get(colorIndex % teamColors.size());
            for (Player p : teams.get(teamName)) {
                p.sendMessage(getMessage("teams.team-name")
                        .replace("{team_name}", teamName)
                        .replace("{team_color}", color.name()));
            }
            colorIndex++;
        }

        Bukkit.getServer().broadcastMessage(getMessage("teams.created")
                .replace("{number_of_teams}", String.valueOf(numTeams)));
    }

    private void handleStartMatch() {
        if (queue.isEmpty()) {
            Bukkit.getServer().broadcastMessage(getMessage("errors.not-enough-players"));
            return;
        }
        Bukkit.getServer().broadcastMessage(getMessage("match.started"));
        queue.clear();
    }

    private String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path, "Съобщението не е зададено."));
    }
}
