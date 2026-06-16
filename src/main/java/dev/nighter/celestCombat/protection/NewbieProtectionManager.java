package dev.nighter.celestCombat.protection;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.player.PlayerProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;

public class NewbieProtectionManager extends BaseProtectionManager {
    private final File protectionFile;
    private FileConfiguration protectionConfig;

    // Tasks
    private Scheduler.Task saveTask;

    // Constants
    private static final long SAVE_INTERVAL = 6000L; // 5 minutes in ticks

    public NewbieProtectionManager(CelestCombat plugin) {
        super(plugin, "newbie_protection", "newbie-protection",
                "newbie_protection_granted", "newbie_protection_attack_blocked",
                "newbie_protection_removed_attack", "newbie_protection_actionbar",
                null);

        this.protectionFile = new File(plugin.getDataFolder(), "newbie_protection_data.yml");

        // Load config and start background tasks
        init();

        // Load protection data from file
        loadProtectionData();

        // Start auto-save task
        startAutoSaveTask();
    }

    @Override
    protected String getDefaultDuration() {
        return "3h";
    }

    @Override
    protected Long checkProfileExpiration(Player player) {
        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        if (!profile.isNewbieProtectionRevoked() && profile.getNewbieProtectionExpiresAt() > System.currentTimeMillis()) {
            return profile.getNewbieProtectionExpiresAt();
        }
        return null;
    }

    @Override
    protected void onGrantProtection(Player player, long expirationTime) {
        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        profile.setNewbieProtectionExpiresAt(expirationTime);
        profile.setNewbieProtectionRevoked(false);
    }

    @Override
    protected void onRemoveProtection(Player player, boolean revoked) {
        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        profile.setNewbieProtectionExpiresAt(0L);
        if (revoked) {
            profile.setNewbieProtectionRevoked(true);
        }
    }

    /**
     * Loads protection data from the YAML file
     */
    private void loadProtectionData() {
        if (!protectionFile.exists()) {
            try {
                protectionFile.getParentFile().mkdirs();
                protectionFile.createNewFile();
                plugin.debug("Created new newbie_protection_data.yml file");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create newbie_protection_data.yml: " + e.getMessage());
                return;
            }
        }

        protectionConfig = YamlConfiguration.loadConfiguration(protectionFile);

        // Load protection data from file
        int loadedCount = 0;
        long currentTime = System.currentTimeMillis();

        for (String uuidStr : protectionConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidStr);
                long expirationTime = protectionConfig.getLong(uuidStr);

                // Only load non-expired protections
                if (expirationTime > currentTime) {
                    protectedPlayers.put(playerUUID, expirationTime);
                    loadedCount++;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in protection data: " + uuidStr);
            }
        }

        plugin.getLogger().info("Loaded " + loadedCount + " active newbie protections");
    }

    /**
     * Saves protection data to the YAML file
     */
    public void saveProtectionData() {
        saveProtectionData(false);
    }

    /**
     * Saves protection data to the YAML file
     * @param synchronous if true, saves synchronously (used during shutdown)
     */
    public void saveProtectionData(boolean synchronous) {
        if (protectionConfig == null) {
            protectionConfig = new YamlConfiguration();
        }

        // Clear existing data
        for (String key : protectionConfig.getKeys(false)) {
            protectionConfig.set(key, null);
        }

        // Save current protections
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : protectedPlayers.entrySet()) {
            // Only save non-expired protections
            if (entry.getValue() > currentTime) {
                protectionConfig.set(entry.getKey().toString(), entry.getValue());
            }
        }

        // Save to file
        if (synchronous || !plugin.isEnabled()) {
            try {
                protectionConfig.save(protectionFile);
                plugin.debug("Saved newbie protection data to file (synchronous)");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save newbie_protection_data.yml: " + e.getMessage());
            }
        } else {
            Scheduler.runTaskAsync(() -> {
                try {
                    protectionConfig.save(protectionFile);
                    plugin.debug("Saved newbie protection data to file");
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save newbie_protection_data.yml: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Starts the auto-save task
     */
    private void startAutoSaveTask() {
        if (saveTask != null) {
            saveTask.cancel();
        }

        saveTask = Scheduler.runTaskTimerAsync(this::saveProtectionData, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    /**
     * Handles player join - grants protection to new players
     */
    public void handlePlayerJoin(Player player) {
        if (!enabled || player == null) return;

        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        if (profile.isNewbieProtectionRevoked()) {
            plugin.debug("Player " + player.getName() + " has revoked newbie protection, not granting again");
            return;
        }

        if (profile.getNewbieProtectionExpiresAt() > System.currentTimeMillis()) {
            protectedPlayers.put(player.getUniqueId(), profile.getNewbieProtectionExpiresAt());
            if (useBossBar) {
                createBossBar(player);
            }
            return;
        }

        if (player.hasPlayedBefore()) {
            plugin.debug("Player " + player.getName() + " has played before, not granting newbie protection");
            return;
        }

        // Grant protection to new player
        grantProtection(player);
    }

    @Override
    public void shutdown() {
        super.shutdown();

        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        // Save data
        saveProtectionData(true);
    }
}
