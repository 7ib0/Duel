package me.tibo.duel;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class Duel extends JavaPlugin {

    private ArenaManager arenaManager;
    private KitManager kitManager;
    private DuelManager duelManager;
    private LeaderboardManager leaderboardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // init managers
        arenaManager = new ArenaManager(this);
        kitManager = new KitManager(this);
        leaderboardManager = new LeaderboardManager(this);
        duelManager = new DuelManager(this, arenaManager, kitManager, leaderboardManager);

        // register commands
        PluginCommand duelCmd = getCommand("duel");
        if (duelCmd != null) {
            duelCmd.setExecutor(duelManager);
        } else {
            getLogger().severe("Failed to register /duel command! Check plugin.yml.");
        }
        PluginCommand duelLbCmd = getCommand("duel-leaderboard");
        if (duelLbCmd != null) {
            duelLbCmd.setExecutor(leaderboardManager);
        }
        PluginCommand duelLbShortCmd = getCommand("duel-lb");
        if (duelLbShortCmd != null) {
            duelLbShortCmd.setExecutor(leaderboardManager);
        }
        PluginCommand duelStatsCmd = getCommand("duel-stats");
        if (duelStatsCmd != null) {
            duelStatsCmd.setExecutor(leaderboardManager);
        }
        PluginCommand duelSpectateCmd = getCommand("spectate");
        if (duelSpectateCmd != null) {
            duelSpectateCmd.setExecutor(duelManager);
        }
        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) {
            kitCmd.setExecutor(kitManager);
        }

        // register duel event listeners
        getServer().getPluginManager().registerEvents(duelManager, this);
    }

    @Override
    public void onDisable() {
        leaderboardManager.saveStats();
        duelManager.cleanup();
        HandlerList.unregisterAll(this);
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
}
