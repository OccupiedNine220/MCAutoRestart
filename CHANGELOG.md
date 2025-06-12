# MCAutoRestart Changelog

## Version 1.3 (2025-06-12)

### Important Notice
**Always create a backup of your server and configuration files before updating!**
```
cp -r plugins/MCAutoRestart plugins/MCAutoRestart_backup
```

### Installation
1. Build from source:
```
mvn clean package
```
Note: Make sure to use `clean` (not "clear") when building with Maven

2. Or download the pre-built JAR from the Releases section
3. Place the JAR file in your server's `plugins` folder
4. Restart your server or use `/reload`

### Added
- **Enhanced language support** with new language files:
  - Ukrainian (uk_ukr.yml)
  - Polish (pl_pol.yml)
  - French (fr_fra.yml)
- **Improved visual notifications system**:
  - Title and subtitle warnings for more visible notifications
  - Actionbar messages for continuous countdown display
  - Sound effects with configurable sounds for different warning levels
  - Visual particle effects around players during warnings

### Improved
- More flexible notification configuration with separate settings for each notification type
- Enhanced language system with better fallback mechanism
- More immersive player experience during restart countdown

### Commands
- New commands for notification management:
  - `/autorestart sounds enable|disable` - Enable or disable sound notifications
  - `/autorestart effects enable|disable` - Enable or disable visual particle effects

### Configuration
- New `notifications.titles` section for configuring title notifications
- New `notifications.actionbar` section for configuring actionbar messages
- New `notifications.sounds` section for configuring sound effects
- New `notifications.visual_effects` section for configuring particle effects