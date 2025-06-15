# MCAutoRestart Changelog

## Version 1.3.2 (2025-06-15)

### Important Notice
**Always create a backup of your server and configuration files before updating!**
```
cp -r plugins/MCAutoRestart plugins/MCAutoRestart_backup
```

### Added
- Added CMI to protected plugins list for better compatibility with popular server management plugin
- **Upgraded Task Scheduler system**:
  - Task priority management (LOW, NORMAL, HIGH priority levels)
  - Task categorization system for better organization
  - Performance monitoring of tasks with execution time tracking
  - Task statistics and error reporting
  - Enhanced API for task management

### Improved
- Improved task scheduling across the plugin:
  - Better BossBar handling using the task scheduler
  - More efficient ActionBar updates
  - More reliable server restart process
  - Better resource management for repeated tasks
- Added new commands for task management:
  - `/autorestart tasks list` - View all running tasks
  - `/autorestart tasks stats` - See performance statistics
  - `/autorestart tasks performance` - Toggle performance monitoring
  - `/autorestart tasks cancel*` - Various task cancellation options
  - `/autorestart tasks priorities` - Manage priority categories

### Fixed
- Fixed potential memory leaks in task scheduling system
- Improved error handling in recurring tasks
- Fixed task cancellation issues when disabling the plugin