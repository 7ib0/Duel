package me.tibo.duel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

class LeaderboardManager implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> duelWins = new HashMap<>();
    private File leaderboardFile;
    private FileConfiguration leaderboardConfig;

    public LeaderboardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadStats();
    }

    private void loadStats() {
        leaderboardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        if (!leaderboardFile.exists()) {
            try {
                leaderboardFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);
        duelWins.clear();
        if (leaderboardConfig.contains("wins")) {
            ConfigurationSection section = leaderboardConfig.getConfigurationSection("wins");
            for (String uuid : section.getKeys(false)) {
                duelWins.put(UUID.fromString(uuid), leaderboardConfig.getInt("wins." + uuid));
            }
        }
    }

    public void saveStats() {
        for (Map.Entry<UUID, Integer> entry : duelWins.entrySet()) {
            leaderboardConfig.set("wins." + entry.getKey().toString(), entry.getValue());
        }
        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void incrementWin(UUID uuid) {
        duelWins.put(uuid, duelWins.getOrDefault(uuid, 0) + 1);
        saveStats();
    }

    public int getWins(UUID uuid) {
        return duelWins.getOrDefault(uuid, 0);
    }

    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(duelWins.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        if (sorted.size() > limit) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }

    // commandEexecutor for /duel-leaderboard, /duel-lb, /duel-stats
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("duel-stats")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            int wins = getWins(uuid);
            player.sendMessage(ChatColor.AQUA + "=== Your Duel Stats ===");
            player.sendMessage(ChatColor.YELLOW + "Wins: " + wins);
            return true;
        }
        List<Map.Entry<UUID, Integer>> sorted = getTopPlayers(10);
        sender.sendMessage(ChatColor.AQUA + "=== Duel Leaderboard ===");
        int i = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString();
            sender.sendMessage(ChatColor.YELLOW + "" + i + ". " + name + ": " + entry.getValue() + " wins");
            if (++i > 10) break;
        }
        return true;
    }
}
