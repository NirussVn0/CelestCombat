package dev.nighter.celestCombat.protection;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.player.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoginProtectionManager {
    private final CelestCombat plugin;
    private final Map<UUID, Long> protectedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Location> freezeLocations = new ConcurrentHashMap<>();

    private boolean enabled;
    private long durationMillis;
    private boolean freezeEnabled;
    private boolean allowLook;
    private boolean clearVelocity;
    private boolean allDamage;
    private boolean knockback;
    private boolean potionEffects;
    private boolean endOnPlayerAttack;
    private boolean endOnPvpCommand;
    private boolean endOnResourcePackLoaded;
    private boolean antiAbuseEnabled;
    private long antiAbuseCooldownMillis;
    private Scheduler.Task cleanupTask;

    public LoginProtectionManager(CelestCombat plugin) {
        this.plugin = plugin;
        reloadConfig();
        startCleanupTask();
    }

    public void reloadConfig() {
        enabled = getBoolean("login_protection.enabled", "login-protection.enabled", true);
        durationMillis = getTimeMillis("login_protection.duration", "login-protection.duration", "10s");
        freezeEnabled = getBoolean("login_protection.freeze.enabled", "login-protection.freeze.enabled", true);
        allowLook = getBoolean("login_protection.freeze.allow_look", "login-protection.freeze.allow-look", true);
        clearVelocity = getBoolean("login_protection.freeze.clear_velocity", "login-protection.freeze.clear-velocity", true);
        allDamage = getBoolean("login_protection.immunity.all_damage", "login-protection.immunity.all-damage", true);
        knockback = getBoolean("login_protection.immunity.knockback", "login-protection.immunity.knockback", true);
        potionEffects = getBoolean("login_protection.immunity.potion_effects", "login-protection.immunity.potion-effects", true);
        endOnPlayerAttack = getBoolean("login_protection.end.on_player_attack", "login-protection.end.on-player-attack", true);
        endOnPvpCommand = getBoolean("login_protection.end.on_pvp_command", "login-protection.end.on-pvp-command", true);
        endOnResourcePackLoaded = getBoolean("login_protection.end.on_resource_pack_loaded", "login-protection.end.on-resource-pack-loaded", true);
        antiAbuseEnabled = getBoolean("login_protection.anti_abuse.disable_when_combat_logged", "login-protection.anti-abuse.disable-when-combat-logged", true);
        antiAbuseCooldownMillis = getTimeMillis("login_protection.anti_abuse.cooldown", "login-protection.anti-abuse.cooldown", "30s");
    }

    private boolean getBoolean(String primaryPath, String legacyPath, boolean defaultValue) {
        return plugin.getConfig().contains(primaryPath)
                ? plugin.getConfig().getBoolean(primaryPath, defaultValue)
                : plugin.getConfig().getBoolean(legacyPath, defaultValue);
    }

    private long getTimeMillis(String primaryPath, String legacyPath, String defaultValue) {
        return plugin.getConfig().contains(primaryPath)
                ? plugin.getTimeFromConfigInMilliseconds(primaryPath, defaultValue)
                : plugin.getTimeFromConfigInMilliseconds(legacyPath, defaultValue);
    }

    public void handlePlayerJoin(Player player) {
        grantProtection(player, durationMillis, true);
    }

    public void grantProtection(Player player, long durationMillis) {
        grantProtection(player, durationMillis, false);
    }

    private void grantProtection(Player player, long durationMillis, boolean enforceAntiAbuse) {
        if (!enabled || player == null) return;

        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        long now = System.currentTimeMillis();
        if (enforceAntiAbuse && antiAbuseEnabled && profile.getLoginProtectionBlockedUntil() > now) {
            plugin.debug("Skipped login protection for " + player.getName() + " due to combat-log anti-abuse");
            return;
        }

        protectedPlayers.put(player.getUniqueId(), now + durationMillis);
        if (freezeEnabled) {
            freezeLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
        if (clearVelocity) {
            player.setVelocity(player.getVelocity().zero());
        }
        plugin.getMessageService().sendMessage(player, "login_protection_started");
    }

    public boolean hasProtection(Player player) {
        if (!enabled || player == null) return false;
        Long expiresAt = protectedPlayers.get(player.getUniqueId());
        if (expiresAt == null) return false;
        if (System.currentTimeMillis() >= expiresAt) {
            removeProtection(player, true);
            return false;
        }
        return true;
    }

    public boolean shouldBlockAllDamage(Player player) {
        return allDamage && hasProtection(player);
    }

    public boolean shouldBlockKnockback(Player player) {
        return knockback && hasProtection(player);
    }

    public boolean shouldBlockPotionEffects(Player player) {
        return potionEffects && hasProtection(player);
    }

    public boolean shouldEndOnPlayerAttack() {
        return endOnPlayerAttack;
    }

    public boolean shouldEndOnPvpCommand() {
        return endOnPvpCommand;
    }

    public boolean shouldEndOnResourcePackLoaded() {
        return endOnResourcePackLoaded;
    }

    public long getAntiAbuseCooldownMillis() {
        return antiAbuseCooldownMillis;
    }

    public void removeProtection(Player player, boolean sendMessage) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        boolean removed = protectedPlayers.remove(uuid) != null;
        freezeLocations.remove(uuid);
        if (removed && sendMessage) {
            plugin.getMessageService().sendMessage(player, "login_protection_ended");
        }
    }

    public void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!freezeEnabled || !hasProtection(player)) return;

        Location anchor = freezeLocations.get(player.getUniqueId());
        Location to = event.getTo();
        if (anchor == null || to == null) return;

        if (anchor.getWorld() != to.getWorld()
                || anchor.getX() != to.getX()
                || anchor.getY() != to.getY()
                || anchor.getZ() != to.getZ()) {
            Location destination = anchor.clone();
            if (allowLook) {
                destination.setYaw(to.getYaw());
                destination.setPitch(to.getPitch());
            }
            event.setTo(destination);
            if (clearVelocity) {
                player.setVelocity(player.getVelocity().zero());
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) return;
        protectedPlayers.remove(player.getUniqueId());
        freezeLocations.remove(player.getUniqueId());
    }

    private void startCleanupTask() {
        cleanupTask = Scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            protectedPlayers.entrySet().removeIf(entry -> {
                if (now < entry.getValue()) return false;
                freezeLocations.remove(entry.getKey());
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null) {
                    plugin.getMessageService().sendMessage(player, "login_protection_ended");
                }
                return true;
            });
        }, 20L, 20L);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        protectedPlayers.clear();
        freezeLocations.clear();
    }
}
