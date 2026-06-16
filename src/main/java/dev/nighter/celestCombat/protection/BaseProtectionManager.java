package dev.nighter.celestCombat.protection;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public abstract class BaseProtectionManager {
    protected final CelestCombat plugin;
    protected final String configKey;
    protected final String legacyKey;

    // Protection storage - UUID -> expiration time in milliseconds
    protected final Map<UUID, Long> protectedPlayers = new ConcurrentHashMap<>();

    // Boss bars for countdown display
    protected final Map<UUID, BossBar> protectionBossBars = new ConcurrentHashMap<>();

    // Configuration cache
    protected boolean enabled;
    protected long durationMillis;
    protected boolean useBossBar;
    protected boolean useActionBar;
    protected String bossBarTitle;
    protected BarColor bossBarColor;
    protected BarStyle bossBarStyle;
    protected final Map<String, Boolean> worldProtectionSettings = new ConcurrentHashMap<>();

    // Damage settings
    protected boolean blockReceivingPvp;
    protected boolean blockDealingPvp;
    protected boolean blockMobs;
    protected boolean blockEnvironmental;
    protected boolean blockAll;
    protected boolean blockKnockback;
    protected boolean blockPotionEffects;

    // Break settings
    protected boolean breakOnPlayerAttack;
    protected boolean breakOnPvpCommand;
    protected boolean breakOnMobAttack;
    protected boolean breakOnDamageReceived;
    protected boolean breakOnMove;
    protected boolean breakOnResourcePackLoaded;

    // Tasks
    protected Scheduler.Task updateTask;
    protected Scheduler.Task cleanupTask;

    // Message Keys
    protected final String messageGrantedKey;
    protected final String messageBlockedKey;
    protected final String messageRemovedAttackKey;
    protected final String messageActionbarKey;
    protected final String messageEndedKey;

    // Constants
    private static final long UPDATE_INTERVAL = 20L; // 1 second in ticks
    private static final long CLEANUP_INTERVAL = 20L; // 1 second in ticks

    protected BaseProtectionManager(CelestCombat plugin, String configKey, String legacyKey,
                                    String messageGrantedKey, String messageBlockedKey,
                                    String messageRemovedAttackKey, String messageActionbarKey,
                                    String messageEndedKey) {
        this.plugin = plugin;
        this.configKey = configKey;
        this.legacyKey = legacyKey;
        this.messageGrantedKey = messageGrantedKey;
        this.messageBlockedKey = messageBlockedKey;
        this.messageRemovedAttackKey = messageRemovedAttackKey;
        this.messageActionbarKey = messageActionbarKey;
        this.messageEndedKey = messageEndedKey;
    }

    public void init() {
        // Load configuration
        loadConfig();

        // Start background tasks
        startUpdateTask();
        startCleanupTask();
    }

    /**
     * Get the default duration string for this protection type (e.g. "3h", "10s", "5m")
     */
    protected abstract String getDefaultDuration();

    /**
     * Defaults for PVP settings
     */
    protected boolean getDefaultBlockReceivingPvp() { return true; }
    protected boolean getDefaultBlockDealingPvp() { return true; }
    protected boolean getDefaultUseBossBar() { return true; }
    protected String getDefaultBossBarTitle() {
        return "&#4CAF50Protection: &#FFFFFF%time%";
    }

    /**
     * Loads configuration values
     */
    public void reloadConfig() {
        loadConfig();
    }

    protected void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        this.enabled = getBoolean(config, "enabled", true);
        this.durationMillis = getTimeMillis(config, "duration", getDefaultDuration());

        // Damage settings
        this.blockReceivingPvp = getBoolean(config, "damage.block_receiving_pvp", getDefaultBlockReceivingPvp(), "protect_from_pvp");
        this.blockDealingPvp = getBoolean(config, "damage.block_dealing_pvp", getDefaultBlockDealingPvp(), "damage.block_dealing_pvp_damage");
        this.blockMobs = getBoolean(config, "damage.block_mobs", false, "protect_from_mobs");
        this.blockEnvironmental = getBoolean(config, "damage.block_environmental", false);
        this.blockAll = getBoolean(config, "damage.block_all", false, "immunity.all_damage");
        this.blockKnockback = getBoolean(config, "damage.block_knockback", false, "immunity.knockback");
        this.blockPotionEffects = getBoolean(config, "damage.block_potion_effects", false, "immunity.potion_effects");

        // Break settings
        this.breakOnPlayerAttack = getBoolean(config, "break.on_player_attack", true, "remove_on_damage_dealt", "end.on_player_attack");
        this.breakOnPvpCommand = getBoolean(config, "break.on_pvp_command", true, "end.on_pvp_command");
        this.breakOnMobAttack = getBoolean(config, "break.on_mob_attack", false);
        this.breakOnDamageReceived = getBoolean(config, "break.on_damage_received", false);
        this.breakOnMove = getBoolean(config, "break.on_move", false);
        this.breakOnResourcePackLoaded = getBoolean(config, "break.on_resource_pack_loaded", false, "end.on_resource_pack_loaded");

        // Display settings
        this.useBossBar = getBoolean(config, "display.use_bossbar", getDefaultUseBossBar());
        this.useActionBar = getBoolean(config, "display.use_actionbar", false);
        this.bossBarTitle = getString(config, "display.bossbar.title", getDefaultBossBarTitle());

        // Parse boss bar color
        String colorStr = getString(config, "display.bossbar.color", "GREEN");
        try {
            this.bossBarColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.bossBarColor = BarColor.GREEN;
            plugin.getLogger().warning("Invalid boss bar color for " + configKey + ": " + colorStr + ", using GREEN");
        }

        // Parse boss bar style
        String styleStr = getString(config, "display.bossbar.style", "SOLID");
        try {
            this.bossBarStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.bossBarStyle = BarStyle.SOLID;
            plugin.getLogger().warning("Invalid boss bar style for " + configKey + ": " + styleStr + ", using SOLID");
        }

        loadWorldProtectionSettings(config);

        plugin.debug(configKey + " config loaded - Enabled: " + enabled +
                ", Duration: " + (durationMillis / 1000) + "s" +
                ", Boss bar: " + useBossBar +
                ", Action bar: " + useActionBar);
    }

    /**
     * Helper to retrieve booleans with legacy fallbacks
     */
    protected boolean getBoolean(FileConfiguration config, String subPath, boolean defaultValue, String... legacySubPaths) {
        String mainPath = configKey + "." + subPath;
        if (config.contains(mainPath)) {
            return config.getBoolean(mainPath);
        }
        String legacyMainPath = legacyKey + "." + subPath.replace('_', '-');
        if (config.contains(legacyMainPath)) {
            return config.getBoolean(legacyMainPath);
        }
        for (String legSub : legacySubPaths) {
            String legPath1 = configKey + "." + legSub;
            if (config.contains(legPath1)) return config.getBoolean(legPath1);
            String legPath2 = legacyKey + "." + legSub.replace('_', '-');
            if (config.contains(legPath2)) return config.getBoolean(legPath2);
        }
        return defaultValue;
    }

    /**
     * Helper to retrieve strings with legacy fallbacks
     */
    protected String getString(FileConfiguration config, String subPath, String defaultValue, String... legacySubPaths) {
        String mainPath = configKey + "." + subPath;
        if (config.contains(mainPath)) {
            return config.getString(mainPath);
        }
        String legacyMainPath = legacyKey + "." + subPath.replace('_', '-');
        if (config.contains(legacyMainPath)) {
            return config.getString(legacyMainPath);
        }
        for (String legSub : legacySubPaths) {
            String legPath1 = configKey + "." + legSub;
            if (config.contains(legPath1)) return config.getString(legPath1);
            String legPath2 = legacyKey + "." + legSub.replace('_', '-');
            if (config.contains(legPath2)) return config.getString(legPath2);
        }
        return defaultValue;
    }

    /**
     * Helper to retrieve duration/time with legacy fallbacks
     */
    protected long getTimeMillis(FileConfiguration config, String subPath, String defaultTimeString, String... legacySubPaths) {
        String mainPath = configKey + "." + subPath;
        if (config.contains(mainPath)) {
            return plugin.getTimeFromConfigInMilliseconds(mainPath, defaultTimeString);
        }
        String legacyMainPath = legacyKey + "." + subPath.replace('_', '-');
        if (config.contains(legacyMainPath)) {
            return plugin.getTimeFromConfigInMilliseconds(legacyMainPath, defaultTimeString);
        }
        for (String legSub : legacySubPaths) {
            String legPath1 = configKey + "." + legSub;
            if (config.contains(legPath1)) return plugin.getTimeFromConfigInMilliseconds(legPath1, defaultTimeString);
            String legPath2 = legacyKey + "." + legSub.replace('_', '-');
            if (config.contains(legPath2)) return plugin.getTimeFromConfigInMilliseconds(legPath2, defaultTimeString);
        }
        return plugin.getTimeFromConfigInMilliseconds(mainPath, defaultTimeString);
    }

    /**
     * Loads per-world settings
     */
    protected void loadWorldProtectionSettings(FileConfiguration config) {
        worldProtectionSettings.clear();
        String worldsSubPath = "worlds";
        String mainPath = configKey + "." + worldsSubPath;
        String legacyMainPath = legacyKey + "." + worldsSubPath;

        String activePath = config.isConfigurationSection(mainPath) ? mainPath :
                (config.isConfigurationSection(legacyMainPath) ? legacyMainPath : null);

        if (activePath != null && config.isConfigurationSection(activePath)) {
            for (String worldName : Objects.requireNonNull(config.getConfigurationSection(activePath)).getKeys(false)) {
                boolean enabledInWorld = config.getBoolean(activePath + "." + worldName, true);
                worldProtectionSettings.put(worldName, enabledInWorld);
            }
        }
    }

    /**
     * Checks if protection is enabled globally and for a specific world
     */
    public boolean isEnabledInWorld(String worldName) {
        if (!enabled) return false;
        return worldProtectionSettings.getOrDefault(worldName, true);
    }

    /**
     * Callback for subclasses when protection is checked but not present in memory
     */
    protected Long checkProfileExpiration(Player player) {
        return null;
    }

    /**
     * Callback for subclasses when protection is granted
     */
    protected void onGrantProtection(Player player, long expirationTime) {}

    /**
     * Callback for subclasses when protection is removed/revoked
     */
    protected void onRemoveProtection(Player player, boolean revoked) {}

    /**
     * Callback for subclasses when protection is removed/revoked for a player who is offline
     */
    protected void onRemoveProtectionOffline(UUID uuid) {}

    /**
     * Checks if a player currently has active protection
     */
    public boolean hasProtection(Player player) {
        if (!enabled || player == null) {
            return false;
        }

        String worldName = player.getWorld().getName();
        if (!isEnabledInWorld(worldName)) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        Long expirationTime = protectedPlayers.get(playerUUID);

        if (expirationTime == null) {
            expirationTime = checkProfileExpiration(player);
            if (expirationTime != null) {
                protectedPlayers.put(playerUUID, expirationTime);
            }
        }

        if (expirationTime == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= expirationTime) {
            removeProtection(player, true);
            return false;
        }

        return true;
    }

    /**
     * Grants protection with the default duration
     */
    public void grantProtection(Player player) {
        grantProtection(player, durationMillis);
    }

    /**
     * Grants protection with a specific duration in milliseconds
     */
    public void grantProtection(Player player, long durationMillis) {
        if (!enabled || player == null) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!isEnabledInWorld(worldName)) {
            plugin.debug("Protection (" + configKey + ") not enabled in world: " + worldName);
            return;
        }

        UUID playerUUID = player.getUniqueId();
        long expirationTime = System.currentTimeMillis() + durationMillis;

        protectedPlayers.put(playerUUID, expirationTime);
        onGrantProtection(player, expirationTime);

        // Create boss bar if enabled
        if (useBossBar) {
            createBossBar(player);
        }

        // Send protection granted message
        sendStartMessage(player, durationMillis);

        plugin.debug("Granted " + configKey + " to " + player.getName() + " until " + new Date(expirationTime));
    }

    /**
     * Removes protection from a player
     */
    public void removeProtection(Player player, boolean sendMessage) {
        removeProtection(player, sendMessage, false);
    }

    /**
     * Revokes protection (e.g. permanently for newbies)
     */
    public void revokeProtection(Player player, boolean sendMessage) {
        removeProtection(player, sendMessage, true);
    }

    protected void removeProtection(Player player, boolean sendMessage, boolean revoked) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        boolean hadProtection = protectedPlayers.remove(playerUUID) != null;
        onRemoveProtection(player, revoked);

        if (hadProtection) {
            // Remove boss bar
            BossBar bossBar = protectionBossBars.remove(playerUUID);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            if (sendMessage) {
                sendEndMessage(player);
            }
            plugin.debug("Removed protection (" + configKey + ") from " + player.getName());
        }
    }

    /**
     * Clears protection for a player (silently)
     */
    public void clearPlayerProtection(Player player) {
        if (player == null) return;
        removeProtection(player, false, false);
    }

    /**
     * Formats remaining time in seconds to a human-readable format
     */
    protected String formatTime(long seconds) {
        if (seconds <= 0) return "0s";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    /**
     * Send start notification
     */
    protected void sendStartMessage(Player player, long durationMillis) {
        if (messageGrantedKey == null || messageGrantedKey.isEmpty()) return;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("duration", formatTime(durationMillis / 1000L));
        plugin.getMessageService().sendMessage(player, messageGrantedKey, placeholders);
    }

    /**
     * Send end notification
     */
    protected void sendEndMessage(Player player) {
        if (messageEndedKey == null || messageEndedKey.isEmpty()) return;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        plugin.getMessageService().sendMessage(player, messageEndedKey, placeholders);
    }

    /**
     * Gets remaining time for a player in seconds
     */
    public long getRemainingTime(Player player) {
        if (!hasProtection(player)) {
            return 0;
        }

        UUID playerUUID = player.getUniqueId();
        Long expirationTime = protectedPlayers.get(playerUUID);

        if (expirationTime == null) {
            return 0;
        }

        long remainingMillis = expirationTime - System.currentTimeMillis();
        return Math.max(0, remainingMillis / 1000);
    }

    /**
     * Creates a boss bar for a player
     */
    protected void createBossBar(Player player) {
        if (!useBossBar || player == null) return;

        UUID playerUUID = player.getUniqueId();

        // Remove existing boss bar if any
        BossBar existingBar = protectionBossBars.get(playerUUID);
        if (existingBar != null) {
            existingBar.removeAll();
        }

        // Create new boss bar
        String title = bossBarTitle.replace("%time%", formatTime(getRemainingTime(player)));
        title = plugin.getLanguageManager().colorize(title);

        BossBar bossBar = Bukkit.createBossBar(title, bossBarColor, bossBarStyle);
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);

        protectionBossBars.put(playerUUID, bossBar);
    }

    /**
     * Updates boss bar title and progress
     */
    protected void updateBossBar(Player player) {
        if (!useBossBar || player == null) return;

        UUID playerUUID = player.getUniqueId();
        BossBar bossBar = protectionBossBars.get(playerUUID);

        if (bossBar == null) return;

        long remainingTime = getRemainingTime(player);
        if (remainingTime <= 0) {
            bossBar.removeAll();
            protectionBossBars.remove(playerUUID);
            return;
        }

        // Update title
        String title = bossBarTitle.replace("%time%", formatTime(remainingTime));
        title = plugin.getLanguageManager().colorize(title);
        bossBar.setTitle(title);

        // Update progress
        double progress = Math.max(0.0, Math.min(1.0, (double) remainingTime / (durationMillis / 1000.0)));
        bossBar.setProgress(progress);
    }

    /**
     * Sends action bar countdown message
     */
    protected void sendActionBar(Player player) {
        if (!useActionBar || player == null || !player.isOnline() || messageActionbarKey == null || messageActionbarKey.isEmpty()) return;

        long remainingTime = getRemainingTime(player);
        if (remainingTime <= 0) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("time", formatTime(remainingTime));
        plugin.getMessageService().sendMessage(player, messageActionbarKey, placeholders);
    }

    /**
     * Starts the periodic update task (runs on main thread for Spigot API calls)
     */
    protected void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Scheduler.runTaskTimer(() -> {
            for (UUID playerUUID : new HashSet<>(protectedPlayers.keySet())) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                if (!hasProtection(player)) {
                    continue; // Will be cleaned up by hasProtection returning false or cleanup task
                }

                if (useBossBar) {
                    updateBossBar(player);
                }

                if (useActionBar) {
                    sendActionBar(player);
                }
            }
        }, 0L, UPDATE_INTERVAL);
    }

    /**
     * Starts the periodic cleanup task (runs on main thread for safety)
     */
    protected void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = Scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : new HashSet<>(protectedPlayers.entrySet())) {
                if (now >= entry.getValue()) {
                    UUID playerUUID = entry.getKey();
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null) {
                        removeProtection(player, true);
                    } else {
                        protectedPlayers.remove(playerUUID);
                        onRemoveProtectionOffline(playerUUID);
                    }
                }
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    /**
     * Handler when dealing PvP damage
     * Returns true if damage should be blocked
     */
    public boolean handleDamageDealt(Player player) {
        if (!hasProtection(player)) {
            return false;
        }

        if (breakOnPlayerAttack) {
            if (messageRemovedAttackKey != null && !messageRemovedAttackKey.isEmpty()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                plugin.getMessageService().sendMessage(player, messageRemovedAttackKey, placeholders);
            }

            revokeProtection(player, false);
            return false; // Break protection, allow damage
        }

        return blockDealingPvp;
    }

    /**
     * Handler when receiving damage
     * Returns true if damage should be blocked
     */
    public boolean handleDamageReceived(Player player, Player attacker) {
        if (!hasProtection(player)) {
            return false;
        }

        if (breakOnDamageReceived) {
            removeProtection(player, true);
            return false; // Break protection, allow damage
        }

        if (attacker != null && attacker != player) {
            if (messageBlockedKey != null && !messageBlockedKey.isEmpty()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("attacker", attacker.getName());
                plugin.getMessageService().sendMessage(attacker, messageBlockedKey, placeholders);
            }
        }

        return true; // Damage is blocked
    }

    /**
     * Centralized damage blocking query for event listener
     */
    public boolean shouldBlockDamage(Player player, EntityDamageEvent event) {
        if (!hasProtection(player)) {
            return false;
        }

        if (blockAll) {
            return true;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            Player attacker = getAttacker(entityEvent);
            if (attacker != null) {
                // PvP damage
                if (blockReceivingPvp) {
                    return handleDamageReceived(player, attacker);
                }
            } else {
                // Mob / Non-player damage
                if (breakOnMobAttack) {
                    removeProtection(player, true);
                    return false;
                }
                if (blockMobs) {
                    return true;
                }
            }
        } else {
            // Environmental/other damage
            if (blockEnvironmental) {
                return true;
            }
        }

        return false;
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    /**
     * Handles clean up when player quits
     */
    public void handlePlayerQuit(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();
        BossBar bossBar = protectionBossBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Getters for checking settings
     */
    public boolean isEnabled() { return enabled; }
    public boolean shouldBlockKnockback(Player player) { return blockKnockback && hasProtection(player); }
    public boolean shouldBlockPotionEffects(Player player) { return blockPotionEffects && hasProtection(player); }
    public boolean shouldBreakOnPvpCommand() { return breakOnPvpCommand; }
    public boolean shouldBreakOnMove() { return breakOnMove; }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        for (BossBar bossBar : protectionBossBars.values()) {
            bossBar.removeAll();
        }
        protectionBossBars.clear();
        protectedPlayers.clear();
    }
}
