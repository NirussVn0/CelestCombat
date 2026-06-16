package dev.nighter.celestCombat.protection;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.player.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoginProtectionManager extends BaseProtectionManager {
    private final Map<UUID, Location> freezeLocations = new ConcurrentHashMap<>();

    private boolean freezeEnabled;
    private boolean allowLook;
    private boolean clearVelocity;
    private boolean antiAbuseEnabled;
    private long antiAbuseCooldownMillis;

    public LoginProtectionManager(CelestCombat plugin) {
        super(plugin, "login_protection", "login-protection",
                "login_protection_started", null,
                null, null,
                "login_protection_ended");
        init();
    }

    @Override
    protected String getDefaultDuration() {
        return "10s";
    }

    @Override
    protected boolean getDefaultUseBossBar() {
        return false;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        FileConfiguration config = plugin.getConfig();
        this.freezeEnabled = getBoolean(config, "freeze.enabled", true);
        this.allowLook = getBoolean(config, "freeze.allow_look", true);
        this.clearVelocity = getBoolean(config, "freeze.clear_velocity", true);
        this.antiAbuseEnabled = getBoolean(config, "anti_abuse.disable_when_combat_logged", true);
        this.antiAbuseCooldownMillis = getTimeMillis(config, "anti_abuse.cooldown", "30s");
    }

    @Override
    protected void onGrantProtection(Player player, long expirationTime) {
        if (freezeEnabled) {
            freezeLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
        if (clearVelocity) {
            player.setVelocity(player.getVelocity().zero());
        }
    }

    @Override
    protected void onRemoveProtection(Player player, boolean revoked) {
        freezeLocations.remove(player.getUniqueId());
    }

    @Override
    protected void onRemoveProtectionOffline(UUID uuid) {
        freezeLocations.remove(uuid);
    }

    public void handlePlayerJoin(Player player) {
        grantProtection(player, durationMillis, true);
    }

    @Override
    public void grantProtection(Player player, long durationMillis) {
        grantProtection(player, durationMillis, false);
    }

    public void grantProtection(Player player, long durationMillis, boolean enforceAntiAbuse) {
        if (!enabled || player == null) return;

        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        long now = System.currentTimeMillis();
        if (enforceAntiAbuse && antiAbuseEnabled && profile.getLoginProtectionBlockedUntil() > now) {
            plugin.debug("Skipped login protection for " + player.getName() + " due to combat-log anti-abuse");
            return;
        }

        super.grantProtection(player, durationMillis);
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

    @Override
    public void handlePlayerQuit(Player player) {
        if (player == null) return;
        super.handlePlayerQuit(player);
        freezeLocations.remove(player.getUniqueId());
    }

    public long getAntiAbuseCooldownMillis() {
        return antiAbuseCooldownMillis;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        freezeLocations.clear();
    }
}
