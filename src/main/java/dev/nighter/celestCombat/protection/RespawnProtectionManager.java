package dev.nighter.celestCombat.protection;

import dev.nighter.celestCombat.CelestCombat;
import org.bukkit.entity.Player;

public class RespawnProtectionManager extends BaseProtectionManager {

    public RespawnProtectionManager(CelestCombat plugin) {
        super(plugin, "respawn_protection", "respawn-protection",
                "respawn_protection_granted", "respawn_protection_attack_blocked",
                "respawn_protection_removed_attack", "respawn_protection_actionbar",
                "respawn_protection_ended");
        init();
    }

    @Override
    protected String getDefaultDuration() {
        return "5m";
    }

    public void handlePlayerRespawn(Player player) {
        if (!enabled || player == null) return;
        
        // Grant respawn protection
        grantProtection(player);
    }
}
