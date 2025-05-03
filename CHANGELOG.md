# MCAutoRestart Changelog

## Version 1.2 (2025-05-03)

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
- **Conditional restart system** with flexible configuration:
  - Restart based on low TPS (ticks per second)
  - Restart based on high memory usage
  - Restart based on low player count
  - All conditional restarts are disabled by default
- **Plugin integration API** for allowing other plugins to:
  - Receive notifications about upcoming restarts
  - Request delay of a restart
  - Request cancellation of a restart
- Added support for Minecraft 1.20.x

### Improved
- Enhanced command system with new commands for managing conditional restarts and API
- Better configuration options with more detailed comments
- More efficient restart scheduling

### Commands
- New commands for conditional restart management:
  - `/autorestart condition` - Show conditional restart status
  - `/autorestart condition enable` - Enable conditional restarts
  - `/autorestart condition disable` - Disable conditional restarts
- New commands for API management:
  - `/autorestart api` - Show API status and active delay requests
  - `/autorestart api reset` - Reset API delay request counters

### Configuration
- New `conditional_restart` section for configuring conditional restarts
- New `api` section for configuring plugin integration options
- Both new features are designed to be very flexible and configurable
