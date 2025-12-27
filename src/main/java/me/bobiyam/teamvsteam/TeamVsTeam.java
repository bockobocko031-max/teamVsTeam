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
import java.sql.*;
import java.util.*;

public final class TeamVsTeam extends JavaPlugin {

    private final List<Player> queue = new ArrayList<>();
    private final Map<String, List<Player>> teams = new LinkedHashMap<>();
    private List<String> teamNames;
    private List<ChatColor> teamColors;
    private FileConfiguration messages;
    private Connection connection;
    private final String dbFile = "teamvsteam.db";
    private FileConfiguration teamLogs;
    private File teamLogsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        showCustomStartupMessage();
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

        setupDatabase();
        loadQueue();
        loadTeams();

        // Зареждане на messages.yml
        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        // Зареждане на team-logs.yml
        File logsFile = new File(getDataFolder(), "team-logs.yml");
        if (!logsFile.exists()) {
            saveResource("team-logs.yml", false);
        }
        teamLogs = YamlConfiguration.loadConfiguration(logsFile);

        getLogger().info("TeamVsTeam plugin е активиран!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TeamVsTeam plugin е деактивиран!");
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
    }

    public Map<String, List<Player>> getTeams() {
        return teams;
    }

    public String getPlayerTeam(Player player) {
        for (String teamName : teams.keySet()) {
            if (teams.get(teamName).contains(player)) return teamName;
        }
        return null;
    }

    private void showCustomStartupMessage() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "\n" +
                ChatColor.RED + "  ████████╗███████╗ ██████╗ ██████╗  █████╗ ███╗   ██╗\n" +
                ChatColor.GOLD + "  ╚══██╔══╝██╔════╝██╔═══██╗██╔══██╗██╔══██╗████╗  ██║\n" +
                ChatColor.YELLOW + "     ██║   █████╗  ██║   ██║██████╔╝███████║██╔██╗ ██║\n" +
                ChatColor.BLUE + "     ██║   ██╔══╝  ██║   ██║██╔══██╗██╔══██║██║╚██╗██║\n" +
                ChatColor.AQUA + "     ██║   ███████╗╚██████╔╝██║  ██║██║  ██║██║ ╚████║\n" +
                ChatColor.DARK_AQUA + "     ╚═╝   ╚══════╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝\n" +
                ChatColor.LIGHT_PURPLE + "  ✦ The Ultimate TeamVsTeam Plugin ✦ \n" +
                ChatColor.GOLD + "  Developed by: BobiYam & PvPBulgaria\n" +
                ChatColor.YELLOW + "  ✧ Version: 2.0 | Fully Compatible with 1.8 - 1.20.x ✧\n" +
                ChatColor.DARK_GREEN + "  ⚡ Optimized for Performance & Stability ⚡\n" +
                ChatColor.GREEN + "  🌍 Official Website: https://pvpbulgaria.eu/\n" +
                ChatColor.BLUE + "  💬 Join our Discord: https://discord.gg/pvpbulgaria\n" +
                ChatColor.DARK_PURPLE + "  🔄 Check Updates & Changelog on our website!\n" +
                ChatColor.GRAY + "  ----------------------------------------------\n" +
                ChatColor.DARK_RED + "  ⭐ Thank you for using TeamVsTeam Plugin! ⭐\n");

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

    private void setupDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, dbFile));

            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS queue (player VARCHAR(36) PRIMARY KEY)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS teams (team_name VARCHAR(50), player VARCHAR(36), PRIMARY KEY(team_name, player))");
            stmt.close();
        } catch (ClassNotFoundException e) {
            getLogger().severe("SQLite драйверът не е намерен!");
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Не можа да се създаде базата данни!");
        }
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

        // Изчистване на структурата в паметта
        teams.clear();
        queue.clear();

        // Изчистване на базата данни
        clearQueueAndTeams();

        admin.sendMessage(ChatColor.GREEN + "Всички отбори и опашки бяха разпуснати успешно!");
    }

    private void loadQueue() {
        try {
            if (connection == null) return;
            PreparedStatement ps = connection.prepareStatement("SELECT player FROM queue");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Player p = Bukkit.getPlayer(UUID.fromString(rs.getString("player")));
                if (p != null) queue.add(p);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadTeamLogs() {
        teamLogsFile = new File(getDataFolder(), "team-logs.yml");
        if (!teamLogsFile.exists()) saveResource("team-logs.yml", false);
        teamLogs = YamlConfiguration.loadConfiguration(teamLogsFile);
    }

    private void logPlayerJoinTeam(String teamName, Player player) {
        String timestamp = java.time.LocalDateTime.now().toString();
        List<String> logList = teamLogs.getStringList(teamName);
        logList.add(timestamp + " - " + player.getName());
        teamLogs.set(teamName, logList);
        try {
            teamLogs.save(teamLogsFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            logPlayerLeaveTeam(teamName, target); // ако използваме логове
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

    private void logPlayerLeaveTeam(String teamName, Player player) {
        String timestamp = java.time.LocalDateTime.now().toString();
        List<String> logList = teamLogs.getStringList(teamName);
        logList.add(timestamp + " - LEFT - " + player.getName());
        teamLogs.set(teamName, logList);
        try {
            teamLogs.save(teamLogsFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadTeams() {
        try {
            if (connection == null) return;
            PreparedStatement ps = connection.prepareStatement("SELECT team_name, player FROM teams");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String teamName = rs.getString("team_name");
                UUID playerUUID = UUID.fromString(rs.getString("player"));
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null) teams.computeIfAbsent(teamName, k -> new ArrayList<>()).add(p);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void addToQueue(Player player) {
        queue.add(player);
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO queue(player) VALUES(?)");
            ps.setString(1, player.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void addToTeam(String teamName, Player player) {
        teams.computeIfAbsent(teamName, k -> new ArrayList<>()).add(player);
        logPlayerJoinTeam(teamName, player); // <-- лог
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO teams(team_name, player) VALUES(?, ?)");
            ps.setString(1, teamName);
            ps.setString(2, player.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void clearQueueAndTeams() {
        queue.clear();
        teams.clear();
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DELETE FROM queue");
            stmt.executeUpdate("DELETE FROM teams");
            stmt.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "==============================");
        player.sendMessage(ChatColor.AQUA + "       TEAM vs TEAM HELP       ");
        player.sendMessage(ChatColor.GOLD + "==============================");

        // Основни команди
        player.sendMessage(ChatColor.YELLOW + "/team join" + ChatColor.WHITE + " - Join a random team in the queue");
        player.sendMessage(ChatColor.YELLOW + "/team help" + ChatColor.WHITE + " - Show this help message");

        // Админ команди
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

    // Играчът join-ва отбор
    private void handleJoin(Player player) {
        if (teams.isEmpty()) {
            // Ако няма създадени отбори, добавяме играча само в queue
            if (queue.contains(player)) {
                player.sendMessage(getMessage("queue.already-in-queue"));
                return;
            }
            queue.add(player);
            player.sendMessage(getMessage("queue.join-success"));
            return;
        }

        // Ако вече има създадени отбори, добавяме играча в случаен отбор
        List<String> keys = new ArrayList<>(teams.keySet());
        String teamName = keys.get(new Random().nextInt(keys.size()));

        if (teams.get(teamName).contains(player)) {
            player.sendMessage(ChatColor.RED + "Вече си в този отбор!");
            return;
        }

        addToTeam(teamName, player);
        queue.add(player); // само за справка кой е join-нал

        player.sendMessage(getMessage("teams.team-name")
                .replace("{team_name}", teamName)
                .replace("{team_color}", ChatColor.GREEN.name()));
    }

    // Създаване на празни отбори
    private void handleCreateTeams(int numTeams) {
        if (numTeams <= 0) {
            Bukkit.broadcastMessage(getMessage("errors.invalid-number-of-teams"));
            return;
        }

        teams.clear();

        for (int i = 0; i < numTeams; i++) {
            String teamName = i < teamNames.size() ? teamNames.get(i) : "Team" + (i + 1);
            teams.put(teamName, new ArrayList<>()); // Празен отбор
        }

        // Изпращаме съобщение само на оператори (OP)
        Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOp)
                .forEach(p -> p.sendMessage(getMessage("teams.created")
                        .replace("{number_of_teams}", String.valueOf(numTeams))));

        // Опционално: показваме имената на отборите само на OP
        int colorIndex = 0;
        for (String teamName : teams.keySet()) {
            ChatColor color = teamColors.get(colorIndex % teamColors.size());
            Bukkit.getOnlinePlayers().stream()
                    .filter(Player::isOp)
                    .forEach(p -> p.sendMessage(getMessage("teams.team-name")
                            .replace("{team_name}", teamName)
                            .replace("{team_color}", color.name())));

            colorIndex++;
        }
    }

    // Стартиране на мач само с хора, които са join-нали
    private void handleStartMatch() {
        if (queue.isEmpty()) {
            Bukkit.broadcastMessage(getMessage("errors.not-enough-players"));
            return;
        }
        Bukkit.broadcastMessage(getMessage("match.started"));
        clearQueueAndTeams();
    }

    public void removeFromQueueDatabase(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM queue WHERE player = ?");
            ps.setString(1, player.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Player> getQueue() {
        return queue;
    }

    public void removeFromTeamDatabase(String teamName, Player player) {
        teams.getOrDefault(teamName, new ArrayList<>()).remove(player);
        logPlayerLeaveTeam(teamName, player); // <-- лог
        try {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM teams WHERE team_name = ? AND player = ?");
            ps.setString(1, teamName);
            ps.setString(2, player.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}