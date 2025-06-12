# MCAutoRestart Changelog

## Version 1.3.1 (2025-06-12) NOT STABLE!!!

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
- **New Task Scheduler system**:
  - Improved task management with error handling
  - Ability to track and manage active tasks
  - Configurable task limits and logging
  
### Fixed
- Fixed incorrect time calculation in the actionbar display
- Fixed potential NullPointerException in the bossbar display
- Fixed various timing issues with notifications

### Commands
- New commands for task management:
  - `/autorestart tasks status` - Show task scheduler status
  - `/autorestart tasks list` - List all active tasks
  - `/autorestart tasks enable|disable` - Enable or disable the task scheduler
  - `/autorestart tasks cancel <id>` - Cancel a specific task
  - `/autorestart tasks cancelall` - Cancel all active tasks

### Configuration
- New `task_scheduler` section in config.yml:
  - `enabled`: Enable or disable the task scheduler (default: true)
  - `max_concurrent_tasks`: Maximum number of concurrent tasks (default: 10)
  - `log_execution`: Whether to log task execution in console (default: true)