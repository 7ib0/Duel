package me.tibo.duel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

class ArenaManager {
    private final JavaPlugin plugin;
    private Arena mainArena;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadArenas();
    }
// load arena's from config
    private void loadArenas() {
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("arenas.bedrock_box.world")) {
            config.set("arenas.bedrock_box.world", "world");
            config.set("arenas.bedrock_box.corner1", Arrays.asList(100, 70, 100));
            config.set("arenas.bedrock_box.corner2", Arrays.asList(114, 75, 114));
            plugin.saveConfig();
        }
        String world = config.getString("arenas.bedrock_box.world");
        List<Integer> c1 = config.getIntegerList("arenas.bedrock_box.corner1");
        List<Integer> c2 = config.getIntegerList("arenas.bedrock_box.corner2");
        mainArena = new Arena(world, c1, c2);
    }

    public Arena getMainArena() {
        return mainArena;
    }

    public static class Arena {
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public Arena(String world, List<Integer> c1, List<Integer> c2) {
            this.world = world;
            this.minX = Math.min(c1.get(0), c2.get(0));
            this.minY = Math.min(c1.get(1), c2.get(1));
            this.minZ = Math.min(c1.get(2), c2.get(2));
            this.maxX = Math.max(c1.get(0), c2.get(0));
            this.maxY = Math.max(c1.get(1), c2.get(1));
            this.maxZ = Math.max(c1.get(2), c2.get(2));
        }

        public Arena offset(int offset) {
            return new Arena(
                    world,
                    Arrays.asList(minX + offset, minY, minZ + offset),
                    Arrays.asList(maxX + offset, maxY, maxZ + offset)
            );
        }

        public Location getCenter() {
            World w = Bukkit.getWorld(world);
            return new Location(w, (minX + maxX) / 2.0 + 0.5, minY + 1, (minZ + maxZ) / 2.0 + 0.5);
        }
// spawn for player 1
        public Location getSpawn1() {
            World w = Bukkit.getWorld(world);
            return new Location(w, minX + 2.5, minY + 1, minZ + 2.5);
        }
// spawn for player 2
        public Location getSpawn2() {
            World w = Bukkit.getWorld(world);
            return new Location(w, maxX - 2.5, minY + 1, maxZ - 2.5);
        }

        public boolean isInside(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().getName().equals(world)) return false;
            return loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                    loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
                    loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }

        public void buildArenaBox() {
            World w = Bukkit.getWorld(world);
            if (w == null) return;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean wall = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                        if (wall) {
                            w.getBlockAt(x, y, z).setType(Material.BEDROCK);
                        } else {
                            w.getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
            }
            for (int x = minX + 1; x < maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) {
                    for (int z = minZ + 1; z < maxZ; z++) {
                        if ((x + y + z) % 7 == 0) {
                            w.getBlockAt(x, y, z).setType(Material.LIGHT); // set lights
                        }
                    }
                }
            }
        }
    }
}

