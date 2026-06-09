# CelestCombat

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/celest-combat-plugin)

A comprehensive combat management plugin for Minecraft servers specializing in PvP environments.

## Requirements

- **Minecraft Version:** 1.21 - 26.1.2
- **Server Software:** Paper, Purpur, Folia
- **Java Version:** 21+

### Optional Dependencies
- **WorldGuard** - For safe zone integration and region protection
- **GriefPrevention** - For claim-based protection systems

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/celestcombat)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/CelestCombat/config.yml`
5. Reload with `/cc reload`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/cc help` | `celestcombat.command.use` | Display command help |
| `/cc reload` | `celestcombat.command.use` | Reload plugin configuration |
| `/cc tag <player1> [player2]` | `celestcombat.command.use` | Manually tag players in combat |
| `/cc removetag <player/world/all>` | `celestcombat.command.use` | Remove combat tags |

**Aliases:** `/cc`, `/combat`, `/celestcombat`

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `celestcombat.command.use` | OP | Access to all plugin commands |
| `celestcombat.update.notify` | OP | Receive update notifications |
| `celestcombat.bypass.tag` | false | Bypass combat tagging |

## Building

```bash
git clone https://github.com/NighterDevelopment/CelestCombat.git
cd CelestCombat
./gradlew build
```

The compiled JAR will be available in `build/libs/`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Support

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/NighterDevelopment/CelestCombat/issues)
- **Discord Community:** [Join our Discord](https://discord.com/invite/FJN7hJKPyb)

## Statistics

[![bStats](https://bstats.org/signatures/bukkit/CelestCombat.svg)](https://bstats.org/plugin/bukkit/CelestCombat/25387)

## License

This project is licensed under the GPLv3 License - see the [LICENSE](LICENSE) file for details.