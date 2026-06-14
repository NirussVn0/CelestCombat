package dev.nighter.celestCombat.commands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PvpCommand implements CommandExecutor, TabCompleter {
    private static final String USE_PERMISSION = "celestcombat.pvp.use";
    private static final String ADMIN_PERMISSION = "celestcombat.pvp.admin";

    private final CelestCombat plugin;

    public PvpCommand(CelestCombat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return enableOwnPvp(sender);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "status" -> handleStatus(sender, args);
            case "on" -> handleAdminToggle(sender, args, true);
            case "off" -> handleAdminToggle(sender, args, false);
            case "reset" -> handleReset(sender, args);
            case "protect" -> handleProtect(sender, args);
            case "unprotect" -> handleUnprotect(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private boolean enableOwnPvp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /pvp.");
            return true;
        }
        if (!sender.hasPermission(USE_PERMISSION)) {
            plugin.getMessageService().sendMessage(sender, "no_permission");
            return true;
        }

        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(player);
        profile.setPvpEnabled(true);

        if (plugin.getLoginProtectionManager().shouldEndOnPvpCommand()) {
            plugin.getLoginProtectionManager().removeProtection(player, true);
        }
        if (plugin.getNewbieProtectionManager().shouldBreakOnPvpCommand()) {
            plugin.getNewbieProtectionManager().revokeProtection(player, false);
        }

        plugin.getPlayerProfileManager().saveProfiles();
        plugin.getMessageService().sendMessage(player, "pvp_enabled_self");
        return true;
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage: /pvp status <player>");
                return;
            }
            sendStatus(sender, player);
            return;
        }

        if (!checkAdmin(sender)) return;
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender, args[1]);
            return;
        }
        sendStatus(sender, target);
    }

    private void sendStatus(CommandSender sender, Player target) {
        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(target);
        String newbie = plugin.getNewbieProtectionManager().hasProtection(target) ? "active" : "inactive";
        String login = plugin.getLoginProtectionManager().hasProtection(target) ? "active" : "inactive";
        plugin.getMessageService().sendMessage(sender, "pvp_status", Map.of(
                "player", target.getName(),
                "pvp", String.valueOf(profile.isPvpEnabled()),
                "newbie", newbie,
                "login", login
        ));
    }

    private void handleAdminToggle(CommandSender sender, String[] args, boolean enabled) {
        if (!checkAdmin(sender)) return;
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /pvp " + (enabled ? "on" : "off") + " <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender, args[1]);
            return;
        }

        plugin.getPlayerProfileManager().getOrCreate(target).setPvpEnabled(enabled);
        plugin.getPlayerProfileManager().saveProfiles();
        plugin.getMessageService().sendMessage(sender, enabled ? "pvp_admin_on" : "pvp_admin_off", Map.of("player", target.getName()));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /pvp reset <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender, args[1]);
            return;
        }

        PlayerProfile profile = plugin.getPlayerProfileManager().getOrCreate(target);
        profile.setPvpEnabled(false);
        profile.setNewbieProtectionRevoked(false);
        profile.setNewbieProtectionExpiresAt(0L);
        profile.setLoginProtectionBlockedUntil(0L);
        plugin.getNewbieProtectionManager().removeProtection(target, false);
        plugin.getLoginProtectionManager().removeProtection(target, false);
        plugin.getPlayerProfileManager().saveProfiles();
        plugin.getMessageService().sendMessage(sender, "pvp_admin_reset", Map.of("player", target.getName()));
    }

    private void handleProtect(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length != 4) {
            sender.sendMessage("§cUsage: /pvp protect <player> <newbie|login> <duration>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender, args[1]);
            return;
        }

        long ticks = plugin.getTimeFormatter().parseTimeToTicks(args[3], -1L);
        if (ticks <= 0) {
            sender.sendMessage("§cInvalid duration: " + args[3]);
            return;
        }

        String type = args[2].toLowerCase(Locale.ROOT);
        if (type.equals("newbie")) {
            plugin.getNewbieProtectionManager().grantProtection(target, ticks * 50L);
        } else if (type.equals("login")) {
            plugin.getLoginProtectionManager().grantProtection(target, ticks * 50L);
        } else {
            sender.sendMessage("§cProtection type must be newbie or login.");
            return;
        }

        plugin.getPlayerProfileManager().saveProfiles();
        plugin.getMessageService().sendMessage(sender, "pvp_admin_protect", Map.of("player", target.getName(), "type", type));
    }

    private void handleUnprotect(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /pvp unprotect <player> <newbie|login|all>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender, args[1]);
            return;
        }

        String type = args[2].toLowerCase(Locale.ROOT);
        if (type.equals("newbie") || type.equals("all")) {
            plugin.getNewbieProtectionManager().revokeProtection(target, false);
        }
        if (type.equals("login") || type.equals("all")) {
            plugin.getLoginProtectionManager().removeProtection(target, false);
        }
        plugin.getPlayerProfileManager().saveProfiles();
        plugin.getMessageService().sendMessage(sender, "pvp_admin_unprotect", Map.of("player", target.getName(), "type", type));
    }

    private void handleReload(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.reloadConfig();
        plugin.refreshTimeCache();
        plugin.getCombatManager().reloadConfig();
        plugin.getKillRewardManager().loadConfig();
        plugin.getNewbieProtectionManager().reloadConfig();
        plugin.getLoginProtectionManager().reloadConfig();
        plugin.getCombatListeners().reload();
        plugin.getMessageService().clearKeyExistsCache();
        plugin.getMessageService().sendMessage(sender, "config_reloaded");
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.getMessageService().sendMessage(sender, "no_permission");
            return false;
        }
        return true;
    }

    private void sendPlayerNotFound(CommandSender sender, String playerName) {
        plugin.getMessageService().sendMessage(sender, "player_not_found", Map.of("player", playerName));
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendMessage(sender, "pvp_help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = sender.hasPermission(ADMIN_PERMISSION)
                    ? Arrays.asList("status", "help", "on", "off", "reset", "protect", "unprotect", "reload")
                    : Arrays.asList("status", "help");
            return filter(values, args[0]);
        }
        if (args.length == 2 && Arrays.asList("status", "on", "off", "reset", "protect", "unprotect").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> startsWith(name, args[1])).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("protect")) {
            return filter(Arrays.asList("newbie", "login"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("unprotect")) {
            return filter(Arrays.asList("newbie", "login", "all"), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> values, String prefix) {
        return values.stream().filter(value -> startsWith(value, prefix)).collect(Collectors.toList());
    }

    private boolean startsWith(String value, String prefix) {
        return value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}
