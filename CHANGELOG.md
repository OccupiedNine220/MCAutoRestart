# MCAutoRestart Changelog

## Version 1.1 (2025-03-22)

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
- Support for Bukkit 1.21.3
- BossBar timer showing countdown to server restart
- BossBar customization (color and style)
- Enhanced compatibility mode with other plugins
- Protected plugins system to prevent task cancellation during restarts
- New languages: Belarusian and Kazakh
- Language selection command: `/autorestart language <code>`

### Improved
- More flexible restart options
- Better notification system
- Enhanced language system with more localized messages
- Automatic player BossBar integration when joining the server
- Fixed variable naming conflicts in the code
- Lambda expression handling for better Java compliance

### Fixed
- Various performance optimizations
- Better error handling for time formats
- Fixed compatibility issues with other plugins
- Resolved compilation issues with variable scopes

### Commands
- New commands for BossBar management:
  - `/autorestart bossbar enable` - Enable BossBar
  - `/autorestart bossbar disable` - Disable BossBar
  - `/autorestart bossbar color <color>` - Change BossBar color
  - `/autorestart bossbar style <style>` - Change BossBar style
- Added compatibility settings command:
  - `/autorestart compatibility <mode>` - Switch between NORMAL and GRACEFUL modes
- Added language switching command:
  - `/autorestart language <lang>` - Change plugin language

### Configuration
- New BossBar settings section
- New compatibility options
- Added new language codes: "be_bel" and "kk_kaz"

### Available Languages
- Russian (ru_rus) - Default
- English (en_eng)
- German (de_deu)
- Spanish (es_esp)
- Belarusian (be_bel) - New!
- Kazakh (kk_kaz) - New! 