package dev.nighter.celestCombat.player;

import java.util.UUID;

public class PlayerProfile {
    private final UUID uuid;
    private boolean pvpEnabled;
    private long newbieProtectionExpiresAt;
    private boolean newbieProtectionRevoked;
    private long loginProtectionBlockedUntil;

    public PlayerProfile(UUID uuid, boolean pvpEnabled, long newbieProtectionExpiresAt,
                         boolean newbieProtectionRevoked, long loginProtectionBlockedUntil) {
        this.uuid = uuid;
        this.pvpEnabled = pvpEnabled;
        this.newbieProtectionExpiresAt = newbieProtectionExpiresAt;
        this.newbieProtectionRevoked = newbieProtectionRevoked;
        this.loginProtectionBlockedUntil = loginProtectionBlockedUntil;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public long getNewbieProtectionExpiresAt() {
        return newbieProtectionExpiresAt;
    }

    public void setNewbieProtectionExpiresAt(long newbieProtectionExpiresAt) {
        this.newbieProtectionExpiresAt = newbieProtectionExpiresAt;
    }

    public boolean isNewbieProtectionRevoked() {
        return newbieProtectionRevoked;
    }

    public void setNewbieProtectionRevoked(boolean newbieProtectionRevoked) {
        this.newbieProtectionRevoked = newbieProtectionRevoked;
    }

    public long getLoginProtectionBlockedUntil() {
        return loginProtectionBlockedUntil;
    }

    public void setLoginProtectionBlockedUntil(long loginProtectionBlockedUntil) {
        this.loginProtectionBlockedUntil = loginProtectionBlockedUntil;
    }
}
