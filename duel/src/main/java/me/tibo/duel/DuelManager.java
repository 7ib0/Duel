package me.tibo.duel;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class DuelManager implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final KitManager kitManager;
    private final LeaderboardManager leaderboardManager;

    private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, DuelSession> activeDuels = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> inDuelMenu = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> duelRequestTimeouts = new ConcurrentHashMap<>();
    private final List<DuelSession> duelSessions = new ArrayList<>();

    public DuelManager(JavaPlugin plugin, ArenaManager arenaManager, KitManager kitManager, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.kitManager = kitManager;
        this.leaderboardManager = leaderboardManager;
    }

    public void cleanup() {
        for (BukkitRunnable task : duelRequestTimeouts.values()) {
            task.cancel();
        }
        duelRequestTimeouts.clear();
        duelSessions.clear();
    }

    public boolean isInDuel(Player p) {
        return activeDuels.containsKey(p.getUniqueId());
    }

    public boolean isInRequest(Player p) {
        return pendingRequests.containsKey(p.getUniqueId());
    }

    // commandexecutor for /duel and /spectate
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player challenger = (Player) sender;
            if (label.equalsIgnoreCase("spectate")) {
                handleSpectateCommand(challenger, args);
                return true;
            }
            if (args.length != 1) {
                challenger.sendMessage(ChatColor.YELLOW + "Usage: /duel <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                challenger.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            if (target.equals(challenger)) {
                challenger.sendMessage(ChatColor.RED + "You cannot duel yourself.");
                return true;
            }
            if (isInDuel(challenger) || isInDuel(target)) {
                challenger.sendMessage(ChatColor.RED + "You or your target is already in a duel.");
                return true;
            }
            if (isInRequest(target)) {
                challenger.sendMessage(ChatColor.RED + "That player already has a pending duel request.");
                return true;
            }
            openKitMenu(challenger, target);
            return true;
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "An error occurred while processing the command. Please contact an admin.");
            ex.printStackTrace();
            return true;
        }
    }

    // kit menu
    public void openKitMenu(Player challenger, Player target) {
        int size = Math.max(9, 9 * ((kitManager.getKits().size() + 2 + 8) / 9));
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.AQUA + "Select a Kit");
        for (KitManager.Kit kit : kitManager.getKits()) {
            ItemStack icon = kit.getIcon();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + kit.name);
                icon.setItemMeta(meta);
            }
            inv.addItem(icon);
        }
        ItemStack ownInv = new ItemStack(Material.CHEST);
        ItemMeta meta = ownInv.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Own Inventory");
            ownInv.setItemMeta(meta);
        }
        inv.addItem(ownInv);
        inDuelMenu.put(challenger.getUniqueId(), true);
        challenger.openInventory(inv);

        // register a listener for this menu
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onInventoryClick(InventoryClickEvent e) {
                if (!e.getWhoClicked().equals(challenger)) return;
                if (!e.getInventory().equals(inv)) return;
                e.setCancelled(true);
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta()) return;
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name.equalsIgnoreCase("Own Inventory")) {
                    List<ItemStack> invContents = Arrays.asList(challenger.getInventory().getContents());
                    List<ItemStack> armor = Arrays.asList(challenger.getInventory().getArmorContents());
                    challenger.closeInventory();
                    HandlerList.unregisterAll(this);
                    openMapMenu(challenger, target, "Own Inventory", invContents, armor);
                    return;
                }
                KitManager.Kit kit = kitManager.getKit(name);
                if (kit == null) {
                    challenger.sendMessage(ChatColor.RED + "Kit not found.");
                    challenger.closeInventory();
                    HandlerList.unregisterAll(this);
                    return;
                }
                challenger.closeInventory();
                HandlerList.unregisterAll(this);
                openMapMenu(challenger, target, kit.name, null, null);
            }
            @EventHandler
            public void onInventoryClose(InventoryCloseEvent e) {
                if (e.getPlayer().equals(challenger)) {
                    HandlerList.unregisterAll(this);
                }
            }
        }, plugin);
    }

    // map menu
    public void openMapMenu(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.AQUA + "Select a Map");
        ItemStack bedrock = new ItemStack(Material.BEDROCK);
        ItemMeta meta = bedrock.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Bedrock Box");
            bedrock.setItemMeta(meta);
        }
        inv.setItem(4, bedrock);
        inDuelMenu.put(challenger.getUniqueId(), true);
        challenger.openInventory(inv);

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onInventoryClick(InventoryClickEvent e) {
                if (!e.getWhoClicked().equals(challenger)) return;
                if (!e.getInventory().equals(inv)) return;
                e.setCancelled(true);
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta()) return;
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name.equalsIgnoreCase("Bedrock Box")) {
                    challenger.closeInventory();
                    HandlerList.unregisterAll(this);
                    sendDuelRequest(challenger, target, kitName, ownInvContents, ownArmor, "Bedrock Box");
                }
            }
            @EventHandler
            public void onInventoryClose(InventoryCloseEvent e) {
                if (e.getPlayer().equals(challenger)) {
                    HandlerList.unregisterAll(this);
                }
            }
        }, plugin);
    }

    // duel request
    public void sendDuelRequest(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, String mapName) {
        DuelRequest req = new DuelRequest(challenger, target, kitName, ownInvContents, ownArmor, mapName);
        pendingRequests.put(target.getUniqueId(), req);
        String accept = ChatColor.GREEN + "[ACCEPT]";
        String deny = ChatColor.RED + "[DENY]";
        target.sendMessage(ChatColor.AQUA + challenger.getName() + " has challenged you to a duel! Kit: " + kitName + ", Map: " + mapName);
        target.sendMessage(accept + " " + deny);
        target.sendMessage(ChatColor.GRAY + "Type 'accept' or 'deny' in chat. This request will expire in 30 seconds.");

        // listen for accept/deny in chat
        Listener chatListener = new Listener() {
            @EventHandler
            public void onPlayerChat(AsyncPlayerChatEvent e) {
                if (!e.getPlayer().equals(target)) return;
                String msg = e.getMessage().trim().toLowerCase();
                if (msg.equals("accept")) {
                    e.setCancelled(true);
                    HandlerList.unregisterAll(this);
                    BukkitRunnable timeout = duelRequestTimeouts.remove(target.getUniqueId());
                    if (timeout != null) timeout.cancel();
                    Bukkit.getScheduler().runTask(plugin, () -> startDuel(req));
                } else if (msg.equals("deny")) {
                    e.setCancelled(true);
                    HandlerList.unregisterAll(this);
                    target.sendMessage(ChatColor.RED + "Duel request denied.");
                    challenger.sendMessage(ChatColor.RED + "Your duel request was denied.");
                    pendingRequests.remove(target.getUniqueId());
                    BukkitRunnable timeout = duelRequestTimeouts.remove(target.getUniqueId());
                    if (timeout != null) timeout.cancel();
                }
            }
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent e) {
                if (e.getPlayer().equals(target) || e.getPlayer().equals(challenger)) {
                    HandlerList.unregisterAll(this);
                    pendingRequests.remove(target.getUniqueId());
                    BukkitRunnable timeout = duelRequestTimeouts.remove(target.getUniqueId());
                    if (timeout != null) timeout.cancel();
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);

        BukkitRunnable timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingRequests.containsKey(target.getUniqueId())) {
                    pendingRequests.remove(target.getUniqueId());
                    HandlerList.unregisterAll(chatListener);
                    challenger.sendMessage(ChatColor.RED + "Your duel request to " + target.getName() + " has expired.");
                    target.sendMessage(ChatColor.RED + "Duel request expired.");
                }
                duelRequestTimeouts.remove(target.getUniqueId());
            }
        };
        timeoutTask.runTaskLater(plugin, 20 * 30);
        duelRequestTimeouts.put(target.getUniqueId(), timeoutTask);

        challenger.sendMessage(ChatColor.YELLOW + "Duel request sent to " + target.getName() + ".");
    }

    private int getNextDuelOffset() {
        synchronized (duelSessions) {
            return duelSessions.size() * 200;
        }
    }

    public List<DuelSession> getActiveDuelSessions() {
        synchronized (duelSessions) {
            return new ArrayList<>(duelSessions);
        }
    }

    public void startDuel(DuelRequest req) {
        Player p1 = req.challenger;
        Player p2 = req.target;
        if (!p1.isOnline() || !p2.isOnline()) {
            if (p1.isOnline()) p1.sendMessage(ChatColor.RED + "Duel cancelled: one player offline.");
            if (p2.isOnline()) p2.sendMessage(ChatColor.RED + "Duel cancelled: one player offline.");
            pendingRequests.remove(p2.getUniqueId());
            BukkitRunnable timeout = duelRequestTimeouts.remove(p2.getUniqueId());
            if (timeout != null) timeout.cancel();
            return;
        }
        if (isInDuel(p1) || isInDuel(p2)) {
            p1.sendMessage(ChatColor.RED + "You or your target is already in a duel.");
            p2.sendMessage(ChatColor.RED + "You or your target is already in a duel.");
            pendingRequests.remove(p2.getUniqueId());
            BukkitRunnable timeout = duelRequestTimeouts.remove(p2.getUniqueId());
            if (timeout != null) timeout.cancel();
            return;
        }

        int duelOffset = getNextDuelOffset();
        ArenaManager.Arena duelArena = arenaManager.getMainArena().offset(duelOffset);

        DuelSession session = new DuelSession(p1, p2, req.kitName, req.ownInvContents, req.ownArmor, req.mapName, duelArena, duelOffset, kitManager, leaderboardManager);
        activeDuels.put(p1.getUniqueId(), session);
        activeDuels.put(p2.getUniqueId(), session);
        synchronized (duelSessions) {
            duelSessions.add(session);
        }
        session.start(plugin);
        pendingRequests.remove(p2.getUniqueId());
        BukkitRunnable timeout = duelRequestTimeouts.remove(p2.getUniqueId());
        if (timeout != null) timeout.cancel();
    }

    public void endDuel(DuelSession session, Player winner, Player loser, String reason) {
        session.end(winner, loser, reason);
        activeDuels.remove(session.p1.getUniqueId());
        activeDuels.remove(session.p2.getUniqueId());
        synchronized (duelSessions) {
            duelSessions.remove(session);
        }
        if (winner != null) leaderboardManager.incrementWin(winner.getUniqueId());
    }

    public void handleSpectateCommand(Player player, String[] args) {
        List<DuelSession> sessions = getActiveDuelSessions();
        if (sessions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "There are no active duels to spectate.");
            return;
        }
        DuelSession session = null;
        if (args.length == 0) {
            session = sessions.get(0);
        } else {
            String name = args[0];
            for (DuelSession s : sessions) {
                if (s.p1.getName().equalsIgnoreCase(name) || s.p2.getName().equalsIgnoreCase(name)) {
                    session = s;
                    break;
                }
            }
            if (session == null) {
                player.sendMessage(ChatColor.RED + "No duel found for player: " + name);
                return;
            }
        }
        Location center = session.getArenaCenter();
        player.teleport(center.clone().add(0, 5, 0));
        player.sendMessage(ChatColor.GREEN + "You are now spectating the duel between " + session.p1.getName() + " and " + session.p2.getName() + ".");
    }



    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isInDuel(p)) {
            DuelSession session = activeDuels.get(p.getUniqueId());
            Player other = session.getOther(p);
            endDuel(session, other, p, "disconnect");
        }
        pendingRequests.remove(p.getUniqueId());
        inDuelMenu.remove(p.getUniqueId());
        BukkitRunnable timeout = duelRequestTimeouts.remove(p.getUniqueId());
        if (timeout != null) timeout.cancel();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (isInDuel(p)) {
            e.getDrops().clear();
            e.setDroppedExp(0);

            DuelSession session = activeDuels.get(p.getUniqueId());
            Player winner = session.getOther(p);
            Bukkit.getScheduler().runTaskLater(plugin, () -> endDuel(session, winner, p, "death"), 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isInDuel(p)) {
            DuelSession session = activeDuels.get(p.getUniqueId());
            if (!session.isInsideArena(p.getLocation())) {
                Location safe = session.getArenaCenter();
                p.teleport(safe);
                p.sendMessage(ChatColor.RED + "You cannot leave the duel arena!");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (inDuelMenu.containsKey(e.getPlayer().getUniqueId())) {
            inDuelMenu.remove(e.getPlayer().getUniqueId());
        }
    }


    public static class DuelSession {
        public final Player p1, p2;
        public final String kitName;
        public final List<ItemStack> ownInvContents;
        public final List<ItemStack> ownArmor;
        public final String mapName;
        public final ArenaManager.Arena arena;
        private final ItemStack[] p1Inv, p2Inv, p1Armor, p2Armor;
        private final Location p1Loc, p2Loc;
        private boolean started = false;
        private final int duelOffset;
        private final KitManager kitManager;
        private final LeaderboardManager leaderboardManager;

        public DuelSession(Player p1, Player p2, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, String mapName, ArenaManager.Arena arena, int duelOffset, KitManager kitManager, LeaderboardManager leaderboardManager) {
            this.p1 = p1;
            this.p2 = p2;
            this.kitName = kitName;
            this.ownInvContents = ownInvContents;
            this.ownArmor = ownArmor;
            this.mapName = mapName;
            this.arena = arena;
            this.duelOffset = duelOffset;
            this.kitManager = kitManager;
            this.leaderboardManager = leaderboardManager;
            this.p1Inv = p1.getInventory().getContents();
            this.p2Inv = p2.getInventory().getContents();
            this.p1Armor = p1.getInventory().getArmorContents();
            this.p2Armor = p2.getInventory().getArmorContents();
            this.p1Loc = p1.getLocation();
            this.p2Loc = p2.getLocation();
        }
        public void start(JavaPlugin plugin) {
            arena.buildArenaBox();
            p1.sendMessage(ChatColor.AQUA + "Duel starting in 3 seconds...");
            p2.sendMessage(ChatColor.AQUA + "Duel starting in 3 seconds...");

            p1.sendMessage(ChatColor.LIGHT_PURPLE + "Type " + ChatColor.YELLOW + "/spectate " + p1.getName() + ChatColor.LIGHT_PURPLE + " to spectate this duel!");
            p2.sendMessage(ChatColor.LIGHT_PURPLE + "Type " + ChatColor.YELLOW + "/spectate " + p1.getName() + ChatColor.LIGHT_PURPLE + " to spectate this duel!");
            new BukkitRunnable() {
                int count = 3;
                @Override
                public void run() {
                    if (count > 0) {
                        p1.sendTitle(ChatColor.RED + "" + count, "", 0, 20, 0);
                        p2.sendTitle(ChatColor.RED + "" + count, "", 0, 20, 0);
                        p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        count--;
                    } else {
                        p1.sendTitle(ChatColor.GREEN + "FIGHT!", "", 0, 20, 0);
                        p2.sendTitle(ChatColor.GREEN + "FIGHT!", "", 0, 20, 0);
                        teleportPlayers();
                        giveKits();
                        started = true;
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
        private void teleportPlayers() {
            p1.teleport(arena.getSpawn1());
            p2.teleport(arena.getSpawn2());
        }
        private void giveKits() {
            p1.getInventory().clear();
            p2.getInventory().clear();
            p1.getInventory().setArmorContents(null);
            p2.getInventory().setArmorContents(null);
            if (kitName.equalsIgnoreCase("Own Inventory")) {
                if (ownInvContents != null && ownArmor != null) {
                    p1.getInventory().setContents(ownInvContents.toArray(new ItemStack[0]));
                    p2.getInventory().setContents(ownInvContents.toArray(new ItemStack[0]));
                    p1.getInventory().setArmorContents(ownArmor.toArray(new ItemStack[0]));
                    p2.getInventory().setArmorContents(ownArmor.toArray(new ItemStack[0]));
                }
            } else {
                KitManager.Kit kit = kitManager.getKit(kitName);
                if (kit != null) {
                    List<ItemStack> items = kit.getItems();
                    List<ItemStack> nonArmor = new ArrayList<>();
                    for (ItemStack item : items) {
                        if (item == null) continue;
                        Material mat = item.getType();
                        String matName = mat.name();
                        if (matName.endsWith("_HELMET") || matName.endsWith("_CHESTPLATE") || matName.endsWith("_LEGGINGS") || matName.endsWith("_BOOTS")) {
                            continue;
                        }
                        nonArmor.add(item.clone());
                    }
                    for (ItemStack item : nonArmor) {
                        p1.getInventory().addItem(item.clone());
                        p2.getInventory().addItem(item.clone());
                    }
                    ItemStack[] armor = kit.getArmorContents();
                    p1.getInventory().setArmorContents(armor);
                    p2.getInventory().setArmorContents(armor);
                }
            }
        }
        public boolean isInsideArena(Location loc) {
            return arena.isInside(loc);
        }
        public Location getArenaCenter() {
            return arena.getCenter();
        }
        public Player getOther(Player p) {
            return p.equals(p1) ? p2 : p1;
        }
        public void end(Player winner, Player loser, String reason) {
            started = false;
            restorePlayers();
            if (winner != null && loser != null) {
                Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " has won a duel against " + loser.getName() + "!");
            }
            p1.teleport(p1Loc);
            p2.teleport(p2Loc);

            // clean up arena
            World w = Bukkit.getWorld(arena.world);
            if (w != null) {
                for (Item item : w.getEntitiesByClass(Item.class)) {
                    Location loc = item.getLocation();
                    if (arena.isInside(loc)) {
                        item.remove();
                    }
                }
            }
        }
        private void restorePlayers() {
            p1.getInventory().setContents(p1Inv);
            p2.getInventory().setContents(p2Inv);
            p1.getInventory().setArmorContents(p1Armor);
            p2.getInventory().setArmorContents(p2Armor);
        }
    }

    public static class DuelRequest {
        public final Player challenger;
        public final Player target;
        public final String kitName;
        public final List<ItemStack> ownInvContents;
        public final List<ItemStack> ownArmor;
        public final String mapName;
        public DuelRequest(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, String mapName) {
            this.challenger = challenger;
            this.target = target;
            this.kitName = kitName;
            this.ownInvContents = ownInvContents;
            this.ownArmor = ownArmor;
            this.mapName = mapName;
        }
    }
}
