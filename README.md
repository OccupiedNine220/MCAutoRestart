# MCAutoRestart

Advanced plugin for automatic Minecraft server restart with flexible settings.

Offical mirror - https://git.qndk.fun/occupiednine220/MCAutoRestart
## Features

- **Two operation modes**: 
  - **Interval mode**: restart every X hours (e.g., every 4 hours)
  - **Fixed mode**: restart at specific times (e.g., at 06:00, 14:00, and 22:00)
- **Customizable messages**: fully customize all plugin messages
- **Multiple language support**: includes Russian, English, German, and Spanish languages
- **Flexible notification settings**: configure timing, frequency, and advance warning times
- **Control commands**: enable/disable restarts, set times and intervals, immediate restart

## Commands

- `/autorestart enable` - Enable automatic restart
- `/autorestart disable` - Disable automatic restart
- `/autorestart status` - View status and time until next restart
- `/autorestart set time HH:MM` - Set a fixed restart time (adds a new time to the list)
- `/autorestart set interval <hours>` - Set restart interval in hours (1-24)
- `/autorestart reload` - Reload plugin configuration
- `/autorestart now` - Perform immediate server restart (requires confirmation)

## Permissions

- `mcautorestart.admin` - Access to all plugin commands (default for operators)

## Configuration

The `config.yml` file contains the following settings:

```yaml
# Включить/отключить автоматические перезапуски (true/false)
# Enable/disable automatic restarts (true/false)
restart-enabled: true

# Режим перезапуска: "interval" (каждые X часов) или "fixed" (в определенное время)
# Restart mode: "interval" (every X hours) or "fixed" (at specific times)
restart-mode: "interval"

# Настройки интервального режима (используется, если restart-mode: "interval")
# Interval mode settings (used if restart-mode: "interval")
interval:
  # Интервал перезапуска в часах (например, 4 для перезапуска каждые 4 часа)
  # Restart interval in hours (e.g., 4 for restart every 4 hours)
  hours: 4
  
  # Минуты в час для перезапуска (0-59)
  # Minutes in the hour for restart (0-59)
  minutes: 5

# Настройки фиксированного режима (используется, если restart-mode: "fixed")
# Fixed mode settings (used if restart-mode: "fixed")
fixed:
  # Список времен перезапуска в формате ЧЧ:ММ (24-часовой формат)
  # List of restart times in HH:MM format (24-hour format)
  times:
    - "06:00"
    - "14:00"
    - "22:00"

# Настройки оповещений
# Notification settings
notifications:
  # Предупреждения о перезапуске
  # Restart warnings
  warnings:
    # Включить/отключить предупреждения
    # Enable/disable warnings
    enabled: true
    
    # До какого времени (в минутах) оповещать игроков перед рестартом
    # How far in advance (in minutes) to notify players before restart
    max_minutes_before: 60
    
    # Времена предупреждений в минутах перед рестартом
    # Warning times in minutes before restart
    minutes: [60, 30, 15, 10, 5, 4, 3, 2, 1]
    
    # Времена предупреждений в секундах перед рестартом (в последнюю минуту)
    # Warning times in seconds before restart (in the last minute)
    seconds: [30, 15, 10, 5, 4, 3, 2, 1]

# Настройки языка
# Language settings
language:
  # Доступные языки: "ru_rus", "en_eng", "de_deu", "es_esp"
  # Available languages: "ru_rus", "en_eng", "de_deu", "es_esp"
  default: "ru_rus"
```

## Language Files

The plugin includes language files that can be edited:
- `plugins/MCAutoRestart/lang/ru_rus.yml` - Russian language
- `plugins/MCAutoRestart/lang/en_eng.yml` - English language
- `plugins/MCAutoRestart/lang/de_deu.yml` - German language
- `plugins/MCAutoRestart/lang/es_esp.yml` - Spanish language

You can add your own language files or customize existing ones to suit your needs.

## Notification Management

The plugin allows for flexible configuration of the notification system:

1. **Enable/disable notifications**: You can completely disable all notifications by setting `notifications.warnings.enabled: false`.

2. **Maximum warning time**: Using the `notifications.warnings.max_minutes_before` parameter, you can specify how many minutes before the restart to begin notifying players. This is useful for servers where very early warnings are not needed.

3. **Time interval configuration**: You can specify any values in the `minutes` and `seconds` lists so notifications appear at exactly these times before the restart.

## Installation

1. Download the latest version of the plugin from the [Releases](https://github.com/mcautorestart/releases) section
2. Place the JAR file in the `plugins` folder of your server
3. Restart the server or enter `/reload`
4. Configure the plugin in the `plugins/MCAutoRestart/config.yml` file

## Requirements

- Minecraft 1.13 or newer
- Java 8 or newer
- Bukkit/Spigot/Paper server

## License

MCAutoRestart is distributed under the MIT license. 
