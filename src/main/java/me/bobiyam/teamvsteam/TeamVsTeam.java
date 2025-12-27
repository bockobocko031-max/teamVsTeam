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

        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

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
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO teams(team_name, player) VALUES(?, ?)");
            ps.setString(1, teamName);
            ps.setString(2, player.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) { e.printStackTrace(); }
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
        player.sendMessage(ChatColor.AQUA + "      TEAM vs TEAM HELP       ");
        player.sendMessage(ChatColor.GOLD + "==============================");
        player.sendMessage(ChatColor.YELLOW + "/team join" + ChatColor.WHITE + " - Join a random team in the queue");
        player.sendMessage(ChatColor.YELLOW + "/team create <number>" + ChatColor.WHITE + " - Create teams (Admin only)");
        player.sendMessage(ChatColor.YELLOW + "/team match" + ChatColor.WHITE + " - Start the match (Admin only)");
        player.sendMessage(ChatColor.YELLOW + "/team help" + ChatColor.WHITE + " - Show this help message");
        player.sendMessage(ChatColor.GOLD + "==============================");
    }

    public String getMessage(String path) {
        String msg = messages.getString(path, "Съобщението не е зададено.");
        String prefix = messages.getString("prefix", "&6[TEAMvsTEAM]&r");
        msg = msg.replace("{prefix}", ChatColor.translateAlternateColorCodes('&', prefix));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private void handleJoin(Player player) {
        if (teams.isEmpty()) {
            player.sendMessage(getMessage("errors.no-teams-created"));
            return;
        }
        if (queue.contains(player)) {
            player.sendMessage(getMessage("queue.already-in-queue"));
            return;
        }

        // Поставяне в рандомен отбор
        List<String> keys = new ArrayList<>(teams.keySet());
        String teamName = keys.get(new Random().nextInt(keys.size()));
        addToTeam(teamName, player);
        addToQueue(player);
        player.sendMessage(getMessage("teams.team-name")
                .replace("{team_name}", teamName)
                .replace("{team_color}", ChatColor.GREEN.name()));
    }

    private void handleCreateTeams(int numTeams) {
        if (queue.isEmpty()) {
            Bukkit.broadcastMessage(getMessage("errors.not-enough-players"));
            return;
        }

        teams.clear();
        List<Player> shuffledPlayers = new ArrayList<>(queue);
        Collections.shuffle(shuffledPlayers);

        for (int i = 0; i < numTeams; i++) {
            String teamName = i < teamNames.size() ? teamNames.get(i) : "Team" + (i + 1);
            teams.put(teamName, new ArrayList<>());
        }

        int totalPlayers = shuffledPlayers.size();
        int base = totalPlayers / numTeams;
        int extra = totalPlayers % numTeams;
        Iterator<Player> it = shuffledPlayers.iterator();
        List<String> keys = new ArrayList<>(teams.keySet());

        for (int i = 0; i < numTeams; i++) {
            int count = base + (i < extra ? 1 : 0);
            List<Player> t = teams.get(keys.get(i));
            for (int j = 0; j < count && it.hasNext(); j++) t.add(it.next());
        }

        int colorIndex = 0;
        for (String tName : teams.keySet()) {
            ChatColor color = teamColors.get(colorIndex % teamColors.size());
            for (Player p : teams.get(tName)) {
                p.sendMessage(getMessage("teams.team-name")
                        .replace("{team_name}", tName)
                        .replace("{team_color}", color.name()));
            }
            colorIndex++;
        }

        Bukkit.broadcastMessage(getMessage("teams.created")
                .replace("{number_of_teams}", String.valueOf(numTeams)));
    }

    private void handleStartMatch() {
        if (queue.isEmpty()) {
            Bukkit.broadcastMessage(getMessage("errors.not-enough-players"));
            return;
        }
        Bukkit.broadcastMessage(getMessage("match.started"));
        clearQueueAndTeams();
    }

    public List<Player> getQueue() {
        return queue;
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

    public void removeFromTeamDatabase(String teamName, Player player) {
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