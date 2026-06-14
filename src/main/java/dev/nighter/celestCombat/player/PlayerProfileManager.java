package dev.nighter.celestCombat.player;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProfileManager {
    private static final long SAVE_INTERVAL = 6000L;

    private final CelestCombat plugin;
    private final File profileFile;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private FileConfiguration profileConfig;
    private Scheduler.Task saveTask;

    public PlayerProfileManager(CelestCombat plugin) {
        this.plugin = plugin;
        this.profileFile = new File(plugin.getDataFolder(), "player_profiles.yml");
        loadProfiles();
        startAutoSave();
    }

    public PlayerProfile getOrCreate(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerProfile(
                uuid,
                player.hasPlayedBefore(),
                0L,
                false,
                0L
        ));
    }

    public PlayerProfile getOrCreate(UUID uuid) {
        return profiles.computeIfAbsent(uuid, id -> new PlayerProfile(id, false, 0L, false, 0L));
    }

    public void markLoginProtectionBlocked(UUID uuid, long blockedUntil) {
        PlayerProfile profile = getOrCreate(uuid);
        profile.setLoginProtectionBlockedUntil(Math.max(profile.getLoginProtectionBlockedUntil(), blockedUntil));
    }

    public void saveProfiles() {
        saveProfiles(false);
    }

    public void saveProfiles(boolean synchronous) {
        Runnable save = () -> {
            FileConfiguration config = new YamlConfiguration();
            for (PlayerProfile profile : profiles.values()) {
                String path = "players." + profile.getUuid();
                config.set(path + ".pvp-enabled", profile.isPvpEnabled());
                config.set(path + ".newbie-protection-expires-at", profile.getNewbieProtectionExpiresAt());
                config.set(path + ".newbie-protection-revoked", profile.isNewbieProtectionRevoked());
                config.set(path + ".login-protection-blocked-until", profile.getLoginProtectionBlockedUntil());
            }

            try {
                profileFile.getParentFile().mkdirs();
                config.save(profileFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save player_profiles.yml: " + e.getMessage());
            }
        };

        if (synchronous || !plugin.isEnabled()) {
            save.run();
        } else {
            Scheduler.runTaskAsync(save);
        }
    }

    private void loadProfiles() {
        if (!profileFile.exists()) {
            profileConfig = new YamlConfiguration();
            return;
        }

        profileConfig = YamlConfiguration.loadConfiguration(profileFile);
        ConfigurationSection section = profileConfig.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String uuidString : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                String path = "players." + uuidString;
                profiles.put(uuid, new PlayerProfile(
                        uuid,
                        profileConfig.getBoolean(path + ".pvp-enabled", false),
                        profileConfig.getLong(path + ".newbie-protection-expires-at", 0L),
                        profileConfig.getBoolean(path + ".newbie-protection-revoked", false),
                        profileConfig.getLong(path + ".login-protection-blocked-until", 0L)
                ));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in player_profiles.yml: " + uuidString);
            }
        }
    }

    private void startAutoSave() {
        saveTask = Scheduler.runTaskTimerAsync(this::saveProfiles, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveProfiles(true);
        profiles.clear();
    }
}
