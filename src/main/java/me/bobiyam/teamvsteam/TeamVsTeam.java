package me.bobiyam.teamvsteam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class TeamVsTeam extends JavaPlugin {

    private final List<Player> queue = new ArrayList<>();
    private final Map<String, List<Player>> teams = new LinkedHashMap<>();
    private List<String> teamNames;
    private List<ChatColor> teamColors;
    private FileConfiguration messages;

    private FileConfiguration logConfig;
    private File logFile;
    private int joinIndex = 0; // циклично разпределяне

    @Override
    public void onEnable() {
        saveDefaultConfig();
//        showCustomStartupMessage();
        reloadConfig();

        getServer().getPluginManager().registerEvents(new TeamListener(this), this);

        // Четене на имена и цветове
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

        // Зареждане на messages.yml
        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        // Зареждане на teams-log.yml
        loadLogFile();

        loadQueue();
        loadTeams();

        getLogger().info("TeamVsTeam plugin е активиран!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TeamVsTeam plugin е деактивиран!");
    }

    private void loadLogFile() {
        logFile = new File(getDataFolder(), "teams-log.yml");
        if (!logFile.exists()) saveResource("teams-log.yml", false);
        logConfig = YamlConfiguration.loadConfiguration(logFile);
    }

    private void saveLogFile() {
        try {
            logConfig.save(logFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPlayerTeam(Player player) {
        for (String teamName : teams.keySet()) {
            if (teams.get(teamName).contains(player)) return teamName;
        }
        return null;
    }

//    private void showCustomStartupMessage() {
//        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "\n" +
//                ChatColor.RED + "  ████████╗███████╗ ██████╗ ██████╗  █████╗ ███╗   ██╗\n" +
//                ChatColor.GOLD + "  ╚══██╔══╝██╔════╝██╔═══██╗██╔══██╗██╔══██╗████╗  ██║\n" +
//                ChatColor.YELLOW + "     ██║   █████╗  ██║   ██║██████╔╝███████║██╔██╗ ██║\n" +
//                ChatColor.BLUE + "     ██║   ██╔══╝  ██║   ██║██╔══██╗██╔══██║██║╚██╗██║\n" +
//                ChatColor.AQUA + "     ██║   ███████╗╚██████╔╝██║  ██║██║  ██║██║ ╚████║\n" +
//                ChatColor.DARK_AQUA + "     ╚═╝   ╚══════╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝\n" +
//                ChatColor.LIGHT_PURPLE + "  ✦ The Ultimate TeamVsTeam Plugin ✦ \n" +
//                ChatColor.GOLD + "  Developed by: BobiYam & PvPBulgaria\n" +
//                ChatColor.YELLOW + "  ✧ Version: 2.0 | Fully Compatible with 1.8 - 1.20.x ✧\n" +
//                ChatColor.DARK_GREEN + "  ⚡ Optimized for Performance & Stability ⚡\n" +
//                ChatColor.GREEN + "  🌍 Official Website: https://pvpbulgaria.eu/\n" +
//                ChatColor.BLUE + "  💬 Join our Discord: https://discord.gg/pvpbulgaria\n" +
//                ChatColor.DARK_PURPLE + "  🔄 Check Updates & Changelog on our website!\n" +
//                ChatColor.GRAY + "  ----------------------------------------------\n" +
//                ChatColor.DARK_RED + "  ⭐ Thank you for using TeamVsTeam Plugin! ⭐\n");
//    }

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

            case "help":
                sendHelpMessage(player);
                break;
            case "disband":
                if (!player.isOp()) {
                    player.sendMessage(getMessage("errors.admin-only"));
                    return true;
                }
                handleDisband(player);
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
            case "kick":
                if (!player.isOp()) {
                    player.sendMessage(getMessage("errors.admin-only"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Моля, въведете името на играча: /team kick <играч>");
                    return true;
                }
                handleKick(player, args[1]);
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

    private void handleDisband(Player admin) {
        if (teams.isEmpty() && queue.isEmpty()) {
            admin.sendMessage(ChatColor.RED + "Няма активни отбори или опашка за разпускане!");
            return;
        }

        // Изпращаме съобщение на всички участници
        for (String teamName : teams.keySet()) {
            for (Player p : teams.get(teamName)) {
                p.sendMessage(ChatColor.RED + "Отборите бяха разпуснати от " + admin.getName() + "!");
            }
        }

        for (Player p : queue) {
            p.sendMessage(ChatColor.RED + "Опашката беше разпусната от " + admin.getName() + "!");
        }

        // Изчистване
        clearQueueAndTeams();

        admin.sendMessage(ChatColor.GREEN + "Всички отбори и опашки бяха разпуснати успешно!");
    }

    private void handleKick(Player admin, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Играчът не е онлайн или името е грешно!");
            return;
        }

        String teamName = getPlayerTeam(target);

        if (teamName != null) {
            teams.get(teamName).remove(target);
            removeFromTeamDatabase(teamName, target);
            target.sendMessage(ChatColor.RED + "Бяхте изгонен от отбора " + teamName + " от " + admin.getName());
            admin.sendMessage(ChatColor.GREEN + "Играчът " + target.getName() + " беше премахнат от отбора " + teamName);
        } else if (queue.contains(target)) {
            queue.remove(target);
            removeFromQueueDatabase(target);
            target.sendMessage(ChatColor.RED + "Бяхте премахнат от опашката от " + admin.getName());
            admin.sendMessage(ChatColor.GREEN + "Играчът " + target.getName() + " беше премахнат от опашката");
        } else {
            admin.sendMessage(ChatColor.RED + "Играчът не е в опашката или в отбор!");
        }
    }

    private void logPlayerJoinTeam(String teamName, Player player) {
        String timestamp = java.time.LocalDateTime.now().toString();
        List<String> logList = logConfig.getStringList("logs." + teamName);
        logList.add(timestamp + " - JOIN - " + player.getName());
        logConfig.set("logs." + teamName, logList);
        saveLogFile();
    }

    private void logPlayerLeaveTeam(String teamName, Player player) {
        String timestamp = java.time.LocalDateTime.now().toString();
        List<String> logList = logConfig.getStringList("logs." + teamName);
        logList.add(timestamp + " - LEFT - " + player.getName());
        logConfig.set("logs." + teamName, logList);
        saveLogFile();
    }

    private void handleCreateTeams(int numTeams) {
        if (numTeams <= 0) return;

        teams.clear();

        for (int i = 0; i < numTeams; i++) {
            String teamName = i < teamNames.size() ? teamNames.get(i) : "Team" + (i + 1);
            teams.put(teamName, new ArrayList<>());
        }

        Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOp)
                .forEach(p -> p.sendMessage(getMessage("teams.created")
                        .replace("{number_of_teams}", String.valueOf(numTeams))));
    }

    private void handleJoin(Player player) {
        if (teams.isEmpty()) {
            player.sendMessage(getMessage("errors.no-teams-created"));
            return;
        }

        for (List<Player> team : teams.values()) {
            if (team.contains(player)) {
                player.sendMessage(ChatColor.RED + "You are already in a team!");
                return;
            }
        }

        List<String> keys = new ArrayList<>(teams.keySet());
        String teamName = keys.get(joinIndex % keys.size());
        joinIndex++;

        addToTeam(teamName, player);

        ChatColor color = teamColors.get(keys.indexOf(teamName) % teamColors.size());
        player.sendMessage(getMessage("teams.team-name")
                .replace("{team_name}", teamName)
                .replace("{team_color}", color.name()));

        player.sendMessage(getMessage("queue.join-success"));
    }

    private void addToTeam(String teamName, Player player) {
        List<Player> team = teams.get(teamName);
        if (team != null && !team.contains(player)) {
            team.add(player);
        }

        List<String> teamList = logConfig.getStringList("teams." + teamName);
        if (!teamList.contains(player.getUniqueId().toString())) {
            teamList.add(player.getUniqueId().toString());
        }
        logConfig.set("teams." + teamName, teamList);
        saveLogFile();

        logPlayerJoinTeam(teamName, player);
    }

    private void handleStartMatch() {
        if (queue.isEmpty()) {
            Bukkit.broadcastMessage(getMessage("errors.not-enough-players"));
            return;
        }
        Bukkit.broadcastMessage(getMessage("match.started"));
        clearQueueAndTeams();
    }

    private void clearQueueAndTeams() {
        queue.clear();
        teams.clear();
        joinIndex = 0;

        logConfig.set("queue", new ArrayList<>());
        logConfig.set("teams", new LinkedHashMap<>());
        saveLogFile();
    }

    private void loadQueue() {
        List<String> queueList = logConfig.getStringList("queue");
        for (String uuid : queueList) {
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));
            if (p != null) queue.add(p);
        }
    }

    private void loadTeams() {
        if (!logConfig.isConfigurationSection("teams")) return;

        for (String teamName : logConfig.getConfigurationSection("teams").getKeys(false)) {
            List<String> playerUUIDs = logConfig.getStringList("teams." + teamName);
            List<Player> teamPlayers = new ArrayList<>();
            for (String uuid : playerUUIDs) {
                Player p = Bukkit.getPlayer(UUID.fromString(uuid));
                if (p != null) teamPlayers.add(p);
            }
            teams.put(teamName, teamPlayers);
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "==============================");
        player.sendMessage(ChatColor.AQUA + "       TEAM vs TEAM HELP       ");
        player.sendMessage(ChatColor.GOLD + "==============================");

        player.sendMessage(ChatColor.YELLOW + "/team join" + ChatColor.WHITE + " - Join a random team in the queue");
        player.sendMessage(ChatColor.YELLOW + "/team help" + ChatColor.WHITE + " - Show this help message");

        player.sendMessage(ChatColor.RED + "----- Admin Commands -----");
        player.sendMessage(ChatColor.RED + "/team kick <player>" + ChatColor.WHITE + " - Kick a player from their team (OP only)");
        player.sendMessage(ChatColor.RED + "/team match" + ChatColor.WHITE + " - Start the match (Admin only)");
        player.sendMessage(ChatColor.RED + "/team create <number>" + ChatColor.WHITE + " - Create teams (Admin only)");
        player.sendMessage(ChatColor.RED + "/team disband" + ChatColor.WHITE + " - Disband all teams and clear the queue (OP only)");

        player.sendMessage(ChatColor.GOLD + "==============================");
    }

    public String getMessage(String path) {
        String msg = messages.getString(path, "Съобщението не е зададено.");
        String prefix = messages.getString("prefix", "&6[TEAMvsTEAM]&r");
        msg = msg.replace("{prefix}", ChatColor.translateAlternateColorCodes('&', prefix));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public void removeFromQueueDatabase(Player player) {
        queue.remove(player);
        List<String> queueList = logConfig.getStringList("queue");
        queueList.remove(player.getUniqueId().toString());
        logConfig.set("queue", queueList);
        saveLogFile();
    }

    public void removeFromTeamDatabase(String teamName, Player player) {
        teams.getOrDefault(teamName, new ArrayList<>()).remove(player);
        logPlayerLeaveTeam(teamName, player);

        List<String> teamList = logConfig.getStringList("teams." + teamName);
        teamList.remove(player.getUniqueId().toString());
        logConfig.set("teams." + teamName, teamList);
        saveLogFile();
    }

    public List<Player> getQueue() {
        return queue;
    }

    public Map<String, List<Player>> getTeams() {
        return teams;
    }
}