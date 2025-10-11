package me.tibo.duel;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

class KitManager implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();
    private File kitsFile;
    private FileConfiguration kitsConfig;

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadKits();
    }

    private void loadKits() {
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            try {
                // set default kits
                kitsFile.getParentFile().mkdirs();
                kitsFile.createNewFile();
                YamlConfiguration def = new YamlConfiguration();
                def.set("kits.cpvp.icon", "DIAMOND_SWORD");
                def.set("kits.cpvp.items", Arrays.asList(
                        "DIAMOND_SWORD",
                        "DIAMOND_HELMET",
                        "DIAMOND_CHESTPLATE",
                        "DIAMOND_LEGGINGS",
                        "DIAMOND_BOOTS",
                        "END_CRYSTAL*64",
                        "END_CRYSTAL*64",
                        "OBSIDIAN*64",
                        "OBSIDIAN*64",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "GOLDEN_APPLE*64",
                        "RESPAWN_ANCHOR*64",
                        "GLOWSTONE*64",
                        "DIAMOND_PICKAXE",
                        "SHIELD",
                        "BOW",
                        "ARROW*64"
                ));
                def.set("kits.swordpvp.icon", "IRON_SWORD");
                def.set("kits.swordpvp.items", Arrays.asList(
                        "IRON_SWORD",
                        "IRON_HELMET",
                        "IRON_CHESTPLATE",
                        "IRON_LEGGINGS",
                        "IRON_BOOTS",
                        "GOLDEN_APPLE*64"
                ));
                def.save(kitsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        kits.clear();
        if (kitsConfig.contains("kits")) {
            ConfigurationSection section = kitsConfig.getConfigurationSection("kits");
            for (String kitName : section.getKeys(false)) {
                String iconName = section.getString(kitName + ".icon", "STONE");
                List<String> itemList = section.getStringList(kitName + ".items");
                Kit kit = new Kit(kitName, iconName, itemList);
                kits.put(kitName.toLowerCase(), kit);
            }
        }
    }

    public Kit getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    public Collection<Kit> getKits() {
        return kits.values();
    }

    public Set<String> getKitNames() {
        return kits.keySet();
    }

    // commandexecutor for /kit
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.AQUA + "Available Kits:");
        for (String kitName : getKitNames()) {
            sender.sendMessage(ChatColor.YELLOW + "- " + kitName);
        }
        return true;
    }

    public static class Kit {
        public final String name;
        public final String iconName;
        public final List<String> itemList;

        public Kit(String name, String iconName, List<String> itemList) {
            this.name = name;
            this.iconName = iconName;
            this.itemList = itemList;
        }

        public ItemStack getIcon() {
            Material mat;
            try {
                mat = Material.valueOf(iconName.toUpperCase());
            } catch (Exception e) {
                mat = Material.STONE;
            }
            return new ItemStack(mat);
        }

        public List<ItemStack> getItems() {
            List<ItemStack> items = new ArrayList<>();
            for (String s : itemList) {
                String[] split = s.split("\\*");
                String matName = split[0];
                int amt = 1;
                if (split.length > 1) {
                    try { amt = Integer.parseInt(split[1]); } catch (Exception ignored) {}
                }
                Material mat;
                try {
                    mat = Material.valueOf(matName.toUpperCase());
                } catch (Exception e) {
                    mat = Material.STONE;
                }
                items.add(new ItemStack(mat, amt));
            }
            return items;
        }

        public ItemStack[] getArmorContents() {
            ItemStack[] armor = new ItemStack[4];
            for (String s : itemList) {
                String[] split = s.split("\\*");
                String matName = split[0].toUpperCase();
                Material mat;
                try {
                    mat = Material.valueOf(matName);
                } catch (Exception e) {
                    continue;
                }
                ItemStack item = new ItemStack(mat, 1);
                if (matName.endsWith("_HELMET")) {
                    armor[3] = item;
                } else if (matName.endsWith("_CHESTPLATE")) {
                    armor[2] = item;
                } else if (matName.endsWith("_LEGGINGS")) {
                    armor[1] = item;
                } else if (matName.endsWith("_BOOTS")) {
                    armor[0] = item;
                }
            }
            return armor;
        }
    }
}

