package com.mcautorestart;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MCAutoRestart extends JavaPlugin {

    private boolean restartEnabled = true;
    private BukkitTask restartTask;
    private Logger logger;
    private FileConfiguration config;
    private Language language;
    private String restartMode;
    private int intervalHours;
    private int intervalMinutes;
    private List<LocalTime> fixedRestartTimes;
    private boolean warningsEnabled;
    private int maxMinutesBefore;
    private List<Integer> warningMinutes;
    private List<Integer> warningSeconds;

    // Поля для боссбара
    private boolean bossbarEnabled;
    private int bossbarShowMinutesBefore;
    private BossBar bossBar;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;
    private BukkitTask bossBarTask;

    // Поля для улучшенной совместимости
    private List<String> protectedPlugins;
    private String compatibilityMode;
    private int gracefulDelaySeconds;

    // Поля для условных рестартов и API
    private ConditionalRestart conditionalRestart;
    private RestartAPI restartAPI;

    // Новые поля для заголовков (titles)
    private boolean titlesEnabled;
    private int titlesShowMinutesBefore;
    private List<Integer> titleMinutes;
    private List<Integer> titleSeconds;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;

    // Новые поля для строки действий (actionbar)
    private boolean actionbarEnabled;
    private int actionbarShowMinutesBefore;
    private int actionbarUpdateFrequency;
    private BukkitTask actionbarTask;

    // Новые поля для звуковых эффектов
    private boolean soundsEnabled;
    private String warningSound;
    private String finalWarningSound;
    private float soundVolume;
    private float soundPitch;

    // Новые поля для визуальных эффектов
    private boolean visualEffectsEnabled;
    private String effectType;
    private int effectCount;
    private double effectRadius;

    @Override
    public void onEnable() {
        logger = getLogger();
        saveDefaultConfig();

        // Инициализация языкового модуля
        language = new Language(this);

        // Загрузка конфигурации
        loadConfig();

        // Инициализация API для интеграции с плагинами
        restartAPI = new RestartAPI(this);

        // Инициализация условных рестартов
        conditionalRestart = new ConditionalRestart(this);
        if (conditionalRestart.isEnabled()) {
            conditionalRestart.startChecking();
        }

        // Регистрация обработчика событий для добавления игроков на боссбар
        getServer().getPluginManager().registerEvents(new BukkitListener(), this);

        // Запуск задачи рестарта, если включено
        if (restartEnabled) {
            scheduleNextRestart();
        }

        logger.info("MCAutoRestart v1.2 включен. Рестарты " +
                (restartEnabled ? "активированы" : "отключены") +
                ". Режим: " + restartMode);

        if (conditionalRestart.isEnabled()) {
            logger.info("Условные рестарты: активированы");
        }

        if (restartAPI.isEnabled()) {
            logger.info("API для интеграции с плагинами: активировано");
        }
    }

    @Override
    public void onDisable() {
        if (restartTask != null) {
            restartTask.cancel();
        }

        // Останавливаем проверки условных рестартов
        if (conditionalRestart != null) {
            conditionalRestart.stopChecking();
        }

        // Скрываем боссбар, если он отображается
        hideBossBar();
        
        // Останавливаем показ строки действий
        stopActionBar();

        logger.info("MCAutoRestart отключен");
    }

    private void loadConfig() {
        config = getConfig();

        // Добавляем значения по умолчанию, если они отсутствуют
        config.addDefault("restart-enabled", true);
        config.addDefault("restart-mode", "interval");
        config.addDefault("interval.hours", 4);
        config.addDefault("interval.minutes", 5);
        config.addDefault("fixed.times", Arrays.asList("06:00", "14:00", "22:00"));
        config.addDefault("notifications.warnings.enabled", true);
        config.addDefault("notifications.warnings.max_minutes_before", 60);
        config.addDefault("notifications.warnings.minutes", Arrays.asList(60, 30, 15, 10, 5, 4, 3, 2, 1));
        config.addDefault("notifications.warnings.seconds", Arrays.asList(30, 15, 10, 5, 4, 3, 2, 1));

        // Добавляем значения по умолчанию для боссбара
        config.addDefault("bossbar.enabled", true);
        config.addDefault("bossbar.show_minutes_before", 10);
        config.addDefault("bossbar.color", "RED");
        config.addDefault("bossbar.style", "SOLID");

        // Добавляем значения по умолчанию для совместимости
        config.addDefault("compatibility.protected_plugins", Arrays.asList("Essentials", "WorldGuard", "LuckPerms"));
        config.addDefault("compatibility.restart_mode", "GRACEFUL");
        config.addDefault("compatibility.graceful_delay_seconds", 5);

        config.addDefault("language.default", "ru_rus");

        config.addDefault("messages.prefix", "&e[MCAutoRestart] &c");
        config.addDefault("messages.restart", "Перезапуск сервера...");
        config.addDefault("messages.warning-minutes", "Внимание! Автоматический рестарт сервера через %time% мин.");
        config.addDefault("messages.warning-seconds", "Внимание! Автоматический рестарт сервера через %time% сек.");

        config.options().copyDefaults(true);
        saveConfig();

        // Загружаем настройки из конфигурации
        restartEnabled = config.getBoolean("restart-enabled");
        restartMode = config.getString("restart-mode", "interval");
        intervalHours = config.getInt("interval.hours", 4);
        intervalMinutes = config.getInt("interval.minutes", 5);

        // Загружаем фиксированные времена рестарта
        List<String> fixedTimesStrings = config.getStringList("fixed.times");
        fixedRestartTimes = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (String timeStr : fixedTimesStrings) {
            try {
                LocalTime time = LocalTime.parse(timeStr, formatter);
                fixedRestartTimes.add(time);
            } catch (DateTimeParseException e) {
                logger.warning("Invalid time format in config: " + timeStr);
            }
        }

        // Загружаем настройки предупреждений
        warningsEnabled = config.getBoolean("notifications.warnings.enabled");
        maxMinutesBefore = config.getInt("notifications.warnings.max_minutes_before");
        warningMinutes = config.getIntegerList("notifications.warnings.minutes");
        warningSeconds = config.getIntegerList("notifications.warnings.seconds");

        // Загружаем настройки боссбара
        bossbarEnabled = config.getBoolean("bossbar.enabled");
        bossbarShowMinutesBefore = config.getInt("bossbar.show_minutes_before");

        try {
            bossBarColor = BarColor.valueOf(config.getString("bossbar.color"));
        } catch (IllegalArgumentException e) {
            bossBarColor = BarColor.RED;
            logger.warning("Invalid bossbar color in config, using RED as default");
        }

        try {
            bossBarStyle = BarStyle.valueOf(config.getString("bossbar.style"));
        } catch (IllegalArgumentException e) {
            bossBarStyle = BarStyle.SOLID;
            logger.warning("Invalid bossbar style in config, using SOLID as default");
        }

        // Загружаем настройки заголовков (titles)
        titlesEnabled = config.getBoolean("notifications.titles.enabled", true);
        titlesShowMinutesBefore = config.getInt("notifications.titles.show_minutes_before", 5);
        titleMinutes = config.getIntegerList("notifications.titles.minutes");
        if (titleMinutes.isEmpty()) {
            titleMinutes = Arrays.asList(5, 3, 2, 1);
        }
        titleSeconds = config.getIntegerList("notifications.titles.seconds");
        if (titleSeconds.isEmpty()) {
            titleSeconds = Arrays.asList(30, 15, 10, 5, 3, 2, 1);
        }
        titleFadeIn = config.getInt("notifications.titles.fade_in", 10);
        titleStay = config.getInt("notifications.titles.stay", 70);
        titleFadeOut = config.getInt("notifications.titles.fade_out", 20);

        // Загружаем настройки строки действий (actionbar)
        actionbarEnabled = config.getBoolean("notifications.actionbar.enabled", true);
        actionbarShowMinutesBefore = config.getInt("notifications.actionbar.show_minutes_before", 3);
        actionbarUpdateFrequency = config.getInt("notifications.actionbar.update_frequency", 20);

        // Загружаем настройки звуковых эффектов
        soundsEnabled = config.getBoolean("notifications.sounds.enabled", true);
        warningSound = config.getString("notifications.sounds.warning_sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        finalWarningSound = config.getString("notifications.sounds.final_warning_sound", "ENTITY_PLAYER_LEVELUP");
        soundVolume = (float) config.getDouble("notifications.sounds.volume", 1.0);
        soundPitch = (float) config.getDouble("notifications.sounds.pitch", 1.0);

        // Загружаем настройки визуальных эффектов
        visualEffectsEnabled = config.getBoolean("notifications.visual_effects.enabled", true);
        effectType = config.getString("notifications.visual_effects.effect_type", "FLAME");
        effectCount = config.getInt("notifications.visual_effects.count", 50);
        effectRadius = config.getDouble("notifications.visual_effects.radius", 1.0);

        // Загружаем настройки совместимости
        protectedPlugins = config.getStringList("compatibility.protected_plugins");
        compatibilityMode = config.getString("compatibility.restart_mode", "GRACEFUL");
        gracefulDelaySeconds = config.getInt("compatibility.graceful_delay_seconds", 5);
    }

    private void saveRestartState() {
        config.set("restart-enabled", restartEnabled);
        saveConfig();
    }

    private void scheduleNextRestart() {
        // Отменяем текущую задачу рестарта, если она существует
        if (restartTask != null) {
            restartTask.cancel();
        }

        // Время до следующего рестарта (в секундах)
        long secondsUntilRestart;

        // Рассчитываем время до рестарта в зависимости от режима
        if ("interval".equals(restartMode)) {
            // Интервальный режим
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRestart = calculateNextIntervalRestart(now);
            secondsUntilRestart = ChronoUnit.SECONDS.between(now, nextRestart);
        } else {
            // Фиксированный режим
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRestart = calculateNextFixedRestart(now);
            secondsUntilRestart = ChronoUnit.SECONDS.between(now, nextRestart);
        }

        // Создаем задачу рестарта
        restartTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            performRestart();
        }, secondsUntilRestart * 20L); // тики (20 тиков = 1 секунда)

        // Если предупреждения включены, планируем их
        if (warningsEnabled) {
            scheduleWarnings(secondsUntilRestart);
        }

        // Если боссбар включен и до рестарта осталось меньше времени, чем задано,
        // показываем боссбар
        if (bossbarEnabled && secondsUntilRestart <= bossbarShowMinutesBefore * 60) {
            showBossBar(secondsUntilRestart);
        } else if (bossbarEnabled) {
            // Планируем показ боссбара через определенное время
            long secondsUntilBossbar = secondsUntilRestart - (bossbarShowMinutesBefore * 60);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // Проверяем, что рестарт все еще активен
                if (restartEnabled) {
                    long remainingSeconds = bossbarShowMinutesBefore * 60;
                    showBossBar(remainingSeconds);
                }
            }, secondsUntilBossbar * 20L);
        }

        logger.info("Запланирован рестарт через " + formatTime(secondsUntilRestart));
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("autorestart")) {
            return false;
        }

        if (!sender.hasPermission("mcautorestart.admin")) {
            sender.sendMessage(language.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "enable":
                restartEnabled = true;
                saveRestartState();
                scheduleNextRestart();
                sender.sendMessage(language.getMessage("messages.enabled"));
                break;

            case "disable":
                restartEnabled = false;
                saveRestartState();
                if (restartTask != null) {
                    restartTask.cancel();
                    restartTask = null;
                }
                if (bossBar != null) {
                    hideBossBar();
                }
                sender.sendMessage(language.getMessage("messages.disabled"));
                break;

            case "reload":
                // Перезагрузка конфигурации
                reloadConfig();
                loadConfig();

                // Перезагрузка языковых файлов
                String defaultLangCode = getConfig().getString("language.default", "ru_rus");
                language.loadLanguage(defaultLangCode);

                // Перезагружаем API и условные рестарты
                if (restartAPI != null) {
                    restartAPI.loadConfig();
                }

                if (conditionalRestart != null) {
                    conditionalRestart.loadConfig();
                    if (conditionalRestart.isEnabled()) {
                        conditionalRestart.startChecking();
                    } else {
                        conditionalRestart.stopChecking();
                    }
                }

                // Перезапуск задачи рестарта, если включено
                if (restartEnabled) {
                    scheduleNextRestart();
                }

                sender.sendMessage(language.getMessage("messages.reload-complete"));
                break;

            case "status":
                showStatus(sender);
                break;

            case "bossbar":
                if (args.length < 2) {
                    showHelp(sender);
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "enable":
                        bossbarEnabled = true;
                        config.set("bossbar.enabled", true);
                        saveConfig();
                        sender.sendMessage(language.getMessage("messages.bossbar-enabled"));

                        // Если рестарт запланирован, обновляем боссбар
                        if (restartEnabled && restartTask != null) {
                            scheduleNextRestart();
                        }
                        break;

                    case "disable":
                        bossbarEnabled = false;
                        config.set("bossbar.enabled", false);
                        saveConfig();
                        hideBossBar();
                        sender.sendMessage(language.getMessage("messages.bossbar-disabled"));
                        break;

                    case "color":
                        if (args.length < 3) {
                            showHelp(sender);
                            return true;
                        }

                        try {
                            BarColor color = BarColor.valueOf(args[2].toUpperCase());
                            bossBarColor = color;
                            config.set("bossbar.color", color.name());
                            saveConfig();
                            sender.sendMessage(
                                    language.getMessage("messages.bossbar-color-changed", "%color%", color.name()));

                            // Обновляем боссбар, если он активен
                            if (bossBar != null) {
                                bossBar.setColor(color);
                            }
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage(language.getMessage("messages.invalid-color"));
                        }
                        break;

                    case "style":
                        if (args.length < 3) {
                            showHelp(sender);
                            return true;
                        }

                        try {
                            BarStyle style = BarStyle.valueOf(args[2].toUpperCase());
                            bossBarStyle = style;
                            config.set("bossbar.style", style.name());
                            saveConfig();
                            sender.sendMessage(
                                    language.getMessage("messages.bossbar-style-changed", "%style%", style.name()));

                            // Обновляем боссбар, если он активен
                            if (bossBar != null) {
                                bossBar.setStyle(style);
                            }
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage(language.getMessage("messages.invalid-style"));
                        }
                        break;

                    default:
                        showHelp(sender);
                        break;
                }
                break;

            case "compatibility":
                if (args.length < 2) {
                    showHelp(sender);
                    return true;
                }

                String mode = args[1].toUpperCase();
                if ("NORMAL".equals(mode) || "GRACEFUL".equals(mode)) {
                    compatibilityMode = mode;
                    config.set("compatibility.restart_mode", mode);
                    saveConfig();
                    sender.sendMessage(language.getMessage("messages.compatibility-mode-set", "%mode%", mode));
                } else {
                    sender.sendMessage(language.getMessage("messages.invalid-mode"));
                }
                break;

            case "language":
                if (args.length < 2) {
                    sender.sendMessage(
                            language.getMessage("messages.current-language", "%lang%", language.getCurrentLang()));
                    return true;
                }

                String langCode = args[1].toLowerCase();
                language.loadLanguage(langCode);
                config.set("language.default", langCode);
                saveConfig();
                sender.sendMessage(language.getMessage("messages.language-changed", "%lang%", langCode));
                break;

            case "sounds":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /autorestart sounds <enable|disable>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("enable")) {
                    soundsEnabled = true;
                    config.set("notifications.sounds.enabled", true);
                    saveConfig();
                    sender.sendMessage(language.getMessage("messages.sound-enabled"));
                } else if (args[1].equalsIgnoreCase("disable")) {
                    soundsEnabled = false;
                    config.set("notifications.sounds.enabled", false);
                    saveConfig();
                    sender.sendMessage(language.getMessage("messages.sound-disabled"));
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /autorestart sounds <enable|disable>");
                }
                break;

            case "effects":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /autorestart effects <enable|disable>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("enable")) {
                    visualEffectsEnabled = true;
                    config.set("notifications.visual_effects.enabled", true);
                    saveConfig();
                    sender.sendMessage(language.getMessage("messages.visual-effects-enabled"));
                } else if (args[1].equalsIgnoreCase("disable")) {
                    visualEffectsEnabled = false;
                    config.set("notifications.visual_effects.enabled", false);
                    saveConfig();
                    sender.sendMessage(language.getMessage("messages.visual-effects-disabled"));
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /autorestart effects <enable|disable>");
                }
                break;

            case "set":
                if (args.length < 3) {
                    showHelp(sender);
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "time":
                        // Установка фиксированного времени рестарта
                        if (args.length < 3) {
                            showHelp(sender);
                            return true;
                        }

                        String timeArg = args[2];
                        try {
                            // Проверяем формат времени
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                            LocalTime time = LocalTime.parse(timeArg, formatter);

                            // Устанавливаем режим "фиксированное время"
                            config.set("restart-mode", "fixed");

                            // Добавляем новое время в список
                            List<String> times = config.getStringList("fixed.times");
                            if (!times.contains(timeArg)) {
                                times.add(timeArg);
                                config.set("fixed.times", times);
                            }

                            saveConfig();
                            loadConfig();

                            // Перепланируем рестарт с новыми настройками
                            if (restartEnabled) {
                                scheduleNextRestart();
                            }

                            sender.sendMessage(language.getMessage("messages.restart-time-set", "%time%", timeArg));
                        } catch (DateTimeParseException e) {
                            sender.sendMessage(language.getMessage("messages.invalid-time"));
                        }
                        break;

                    case "interval":
                        // Установка интервала рестарта
                        if (args.length < 3) {
                            showHelp(sender);
                            return true;
                        }

                        try {
                            int hours = Integer.parseInt(args[2]);
                            if (hours < 1 || hours > 24) {
                                sender.sendMessage(language.getMessage("messages.invalid-hours"));
                                return true;
                            }

                            // Устанавливаем режим "интервал"
                            config.set("restart-mode", "interval");
                            config.set("interval.hours", hours);

                            saveConfig();
                            loadConfig();

                            // Перепланируем рестарт с новыми настройками
                            if (restartEnabled) {
                                scheduleNextRestart();
                            }

                            sender.sendMessage(
                                    language.getMessage("messages.interval-set", "%hours%", String.valueOf(hours)));
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Пожалуйста, укажите корректное число часов.");
                        }
                        break;

                    default:
                        showHelp(sender);
                        break;
                }
                break;

            case "now":
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(language.getMessage("messages.confirm-restart"));
                    return true;
                }

                sender.sendMessage(language.getMessage("messages.performing-restart"));
                restartServer("manual");
                break;

            case "condition":
                if (args.length < 2) {
                    // Показываем статус условных рестартов
                    sender.sendMessage(conditionalRestart.getStatus());
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "enable":
                        conditionalRestart.setEnabled(true);
                        sender.sendMessage(language.getMessage("messages.conditional-restart-enabled"));
                        break;

                    case "disable":
                        conditionalRestart.setEnabled(false);
                        sender.sendMessage(language.getMessage("messages.conditional-restart-disabled"));
                        break;

                    case "status":
                        sender.sendMessage(conditionalRestart.getStatus());
                        break;

                    default:
                        sender.sendMessage(language.getMessage("messages.invalid-args"));
                        break;
                }
                break;

            case "api":
                if (args.length < 2) {
                    // Показываем статус API
                    sender.sendMessage(language.getMessage("messages.api-status",
                            "%enabled%", restartAPI.isEnabled() ? "enabled" : "disabled"));

                    if (restartAPI.isEnabled() && restartAPI.hasActiveDelayRequests()) {
                        StringBuilder delayInfo = new StringBuilder(language.getMessage("messages.api-active-delays"));
                        for (RestartAPI.RestartDelayRequest request : restartAPI.getActiveDelayRequests()) {
                            delayInfo.append("\n").append(language.getMessage("messages.api-delay-info",
                                    "%plugin%", request.getPluginName(),
                                    "%minutes%", String.valueOf(request.getDelayMinutes()),
                                    "%reason%", request.getReason()));
                        }
                        sender.sendMessage(delayInfo.toString());
                    }

                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "reset":
                        restartAPI.resetDelayRequestsCount();
                        sender.sendMessage(language.getMessage("messages.api-reset"));
                        break;

                    default:
                        sender.sendMessage(language.getMessage("messages.invalid-args"));
                        break;
                }
                break;

            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void showStatus(CommandSender sender) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRestart;

        // Определяем следующее время перезапуска
        if (restartMode.equalsIgnoreCase("interval")) {
            // Интервальный режим
            nextRestart = calculateNextIntervalRestart(now);
        } else {
            // Фиксированный режим
            nextRestart = calculateNextFixedRestart(now);
        }

        long delaySeconds = now.until(nextRestart, ChronoUnit.SECONDS);

        sender.sendMessage(language.getMessage("status.title"));

        String stateEnabled = language.getMessage("status.state-enabled");
        String stateDisabled = language.getMessage("status.state-disabled");

        sender.sendMessage(language.getMessage("status.state",
                "%state%", restartEnabled ? stateEnabled : stateDisabled));

        if (restartEnabled) {
            // Форматирование времени следующего рестарта
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            sender.sendMessage(language.getMessage("status.next-restart",
                    "%time%", nextRestart.format(formatter)));

            sender.sendMessage(language.getMessage("status.time-until",
                    "%time%", formatTime(delaySeconds)));

            if (restartMode.equalsIgnoreCase("interval")) {
                // Интервальный режим
                sender.sendMessage(language.getMessage("status.mode",
                        "%mode%", language.getMessage("status.mode-interval",
                                "%hours%", String.valueOf(intervalHours))));
            } else {
                // Фиксированный режим
                StringBuilder timesStr = new StringBuilder();
                for (int i = 0; i < fixedRestartTimes.size(); i++) {
                    timesStr.append(fixedRestartTimes.get(i).format(formatter));
                    if (i < fixedRestartTimes.size() - 1) {
                        timesStr.append(", ");
                    }
                }

                sender.sendMessage(language.getMessage("status.mode",
                        "%mode%", language.getMessage("status.mode-fixed",
                                "%time%", timesStr.toString())));
            }

            // Статус боссбара
            sender.sendMessage(language.getMessage("status.bossbar",
                    "%state%", bossbarEnabled ? stateEnabled : stateDisabled));

            // Режим совместимости
            sender.sendMessage(language.getMessage("status.compatibility-mode",
                    "%mode%", compatibilityMode));

            // Текущий язык
            sender.sendMessage(language.getMessage("status.current-language",
                    "%language%", language.getCurrentLang()));

            // Статус условных рестартов
            sender.sendMessage(language.getMessage("status.conditional-restart",
                    "%state%",
                    conditionalRestart != null && conditionalRestart.isEnabled() ? stateEnabled : stateDisabled));

            // Статус API
            sender.sendMessage(language.getMessage("status.api",
                    "%state%", restartAPI != null && restartAPI.isEnabled() ? stateEnabled : stateDisabled));

            // Информация о задержках API
            if (restartAPI != null && restartAPI.isEnabled() && restartAPI.hasActiveDelayRequests()) {
                sender.sendMessage(language.getMessage("status.api-delays",
                        "%count%", String.valueOf(restartAPI.getActiveDelayRequests().size()),
                        "%minutes%", String.valueOf(restartAPI.getMaxActiveDelayMinutes())));
            }
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(language.getMessage("help.title"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.enable"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.disable"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.status"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.set-time"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.set-interval"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.reload"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.now"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.bossbar-enable"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.bossbar-disable"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.bossbar-color"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.bossbar-style"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.compatibility"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.language"));
        
        // Новые команды для визуальных оповещений
        sender.sendMessage(ChatColor.YELLOW + "/autorestart sounds <enable|disable> - Включить/отключить звуковые оповещения");
        sender.sendMessage(ChatColor.YELLOW + "/autorestart effects <enable|disable> - Включить/отключить визуальные эффекты");

        // Новые команды для условных рестартов
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.condition-status"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.condition-enable"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.condition-disable"));

        // Новые команды для API
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.api-status"));
        sender.sendMessage(ChatColor.YELLOW + language.getMessage("help.api-reset"));
    }

    /**
     * Создает и показывает боссбар, отображающий время до рестарта
     * 
     * @param secondsUntilRestart количество секунд до рестарта
     */
    private void showBossBar(long secondsUntilRestart) {
        // Если боссбар уже существует, отменяем связанную задачу
        if (bossBar != null) {
            if (bossBarTask != null) {
                bossBarTask.cancel();
            }
            bossBar.removeAll();
            Bukkit.getScheduler().cancelTask(bossBarTask.getTaskId());
        }

        // Создаем новый боссбар
        bossBar = Bukkit.createBossBar(
                language.getMessage("messages.bossbar-title", "%time%", formatTime(secondsUntilRestart)),
                bossBarColor,
                bossBarStyle);

        // Добавляем всех игроков на сервере
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        // Используем AtomicLong для безопасного изменения в лямбде
        final java.util.concurrent.atomic.AtomicLong remainingSeconds = new java.util.concurrent.atomic.AtomicLong(
                secondsUntilRestart);

        // Создаем задачу для обновления боссбара
        bossBarTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long seconds = remainingSeconds.decrementAndGet();

            if (seconds <= 0) {
                // Время вышло, удаляем боссбар
                bossBar.removeAll();
                bossBarTask.cancel();
                return;
            }

            // Обновляем текст боссбара
            bossBar.setTitle(language.getMessage("messages.bossbar-title", "%time%", formatTime(seconds)));

            // Обновляем прогресс боссбара (от 1.0 до 0.0)
            double progress = Math.max(0.0, Math.min(1.0, seconds / (bossbarShowMinutesBefore * 60.0)));
            bossBar.setProgress(progress);

            // Меняем цвет боссбара в зависимости от оставшегося времени
            if (seconds <= 60) {
                bossBar.setColor(BarColor.RED);
            } else if (seconds <= 300) {
                bossBar.setColor(BarColor.YELLOW);
            }
        }, 0L, 20L); // Обновляем каждую секунду
    }

    /**
     * Скрывает боссбар, если он существует
     */
    private void hideBossBar() {
        if (bossBar != null) {
            bossBar.setVisible(false);
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.removePlayer(player);
            }
            bossBar = null;
        }
        
        if (bossBarTask != null) {
            bossBarTask.cancel();
            bossBarTask = null;
        }
    }

    /**
     * Обрабатывает появление нового игрока для добавления его к боссбару
     * 
     * @param player игрок, который присоединился
     */
    public void addPlayerToBossBar(Player player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    /**
     * Рассчитывает время следующего рестарта для интервального режима
     * 
     * @param now текущее время
     * @return время следующего рестарта
     */
    private LocalDateTime calculateNextIntervalRestart(LocalDateTime now) {
        // Определяем следующее время перезапуска
        int currentHour = now.getHour();
        int targetHour = (currentHour / intervalHours) * intervalHours;

        if (now.getHour() == targetHour && now.getMinute() >= intervalMinutes) {
            // Если мы уже прошли время перезапуска в текущем интервале, переходим к
            // следующему
            targetHour = (targetHour + intervalHours) % 24;
        }

        LocalDateTime nextRestart = now.withHour(targetHour)
                .withMinute(intervalMinutes)
                .withSecond(0)
                .withNano(0);

        // Если расчётное время рестарта в прошлом, добавляем интервал
        if (nextRestart.isBefore(now)) {
            nextRestart = nextRestart.plusHours(intervalHours);
        }

        return nextRestart;
    }

    /**
     * Рассчитывает время следующего рестарта для фиксированного режима
     * 
     * @param now текущее время
     * @return время следующего рестарта
     */
    private LocalDateTime calculateNextFixedRestart(LocalDateTime now) {
        // Находим ближайшее время рестарта
        LocalTime currentTime = now.toLocalTime();
        LocalTime closestTime = null;
        long minDiff = Long.MAX_VALUE;

        for (LocalTime restartTime : fixedRestartTimes) {
            long seconds = currentTime.until(restartTime, ChronoUnit.SECONDS);

            // Если время рестарта уже прошло, добавляем 24 часа
            if (seconds < 0) {
                seconds += 24 * 60 * 60;
            }

            if (seconds < minDiff) {
                minDiff = seconds;
                closestTime = restartTime;
            }
        }

        if (closestTime == null) {
            logger.severe("Не удалось определить время следующего рестарта. Времена рестарта не указаны.");
            return now.plusHours(24); // Если что-то пошло не так, рестарт через 24 часа
        }

        // Создаем DateTime для следующего рестарта
        LocalDateTime nextRestart = now.withHour(closestTime.getHour())
                .withMinute(closestTime.getMinute())
                .withSecond(0)
                .withNano(0);

        // Если расчётное время рестарта в прошлом, добавляем день
        if (nextRestart.isBefore(now)) {
            nextRestart = nextRestart.plusDays(1);
        }

        return nextRestart;
    }

    /**
     * Планирует предупреждения о перезапуске
     * 
     * @param secondsUntilRestart количество секунд до рестарта
     */
    private void scheduleWarnings(long secondsUntilRestart) {
        if (!warningsEnabled) {
            return;
        }
        
        // Планируем предупреждения перед рестартом (в минутах)
        for (int warningTime : warningMinutes) {
            long warningDelaySeconds = secondsUntilRestart - (warningTime * 60);
            if (warningDelaySeconds > 0 && warningTime <= maxMinutesBefore) {
                final int finalWarningTime = warningTime;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // Отправляем сообщение в чат
                    Bukkit.broadcastMessage(language.getMessage("messages.warning-minutes", "%time%",
                            String.valueOf(finalWarningTime)));
                            
                    // Проигрываем звук предупреждения
                    playWarningSound(false);
                    
                    // Показываем визуальные эффекты
                    if (visualEffectsEnabled) {
                        showVisualEffects();
                    }
                    
                    // Показываем заголовок, если он включен и настроен для этого времени
                    if (titlesEnabled && titleMinutes.contains(finalWarningTime) && finalWarningTime <= titlesShowMinutesBefore) {
                        showTitle(finalWarningTime * 60);
                    }
                }, warningDelaySeconds * 20L);
            }
        }

        // Предупреждения в последнюю минуту (в секундах)
        for (int sec : warningSeconds) {
            long warningDelaySeconds = secondsUntilRestart - sec;
            if (warningDelaySeconds > 0) {
                final int finalSec = sec;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // Отправляем сообщение в чат
                    Bukkit.broadcastMessage(
                            language.getMessage("messages.warning-seconds", "%time%", String.valueOf(finalSec)));
                            
                    // Проигрываем звук финального предупреждения для последних секунд
                    playWarningSound(finalSec <= 10);
                    
                    // Показываем визуальные эффекты
                    if (visualEffectsEnabled) {
                        showVisualEffects();
                    }
                    
                    // Показываем заголовок, если он включен и настроен для этого времени
                    if (titlesEnabled && titleSeconds.contains(finalSec)) {
                        showTitle(finalSec);
                    }
                }, warningDelaySeconds * 20L);
            }
        }
        
        // Показать боссбар, если включен
        if (bossbarEnabled && secondsUntilRestart <= bossbarShowMinutesBefore * 60) {
            showBossBar(secondsUntilRestart);
        } else {
            // Запланировать показ боссбара позже
            long secondsToShowBossBar = secondsUntilRestart - (bossbarShowMinutesBefore * 60);
            if (secondsToShowBossBar > 0 && bossbarEnabled) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    showBossBar(bossbarShowMinutesBefore * 60);
                }, secondsToShowBossBar * 20L);
            }
        }
        
        // Запустить строку действий (actionbar), если включена
        if (actionbarEnabled && secondsUntilRestart <= actionbarShowMinutesBefore * 60) {
            startActionBar(secondsUntilRestart);
        } else {
            // Запланировать показ строки действий позже
            long secondsToShowActionBar = secondsUntilRestart - (actionbarShowMinutesBefore * 60);
            if (secondsToShowActionBar > 0 && actionbarEnabled) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    startActionBar(actionbarShowMinutesBefore * 60);
                }, secondsToShowActionBar * 20L);
            }
        }
    }

    /**
     * Выполняет рестарт сервера с учетом настроек совместимости
     */
    private void performRestart() {
        Bukkit.broadcastMessage(language.getMessage("messages.restart"));

        if ("GRACEFUL".equalsIgnoreCase(compatibilityMode)) {
            // Плавный рестарт с защитой плагинов
            logger.info(language.getMessage("messages.graceful-restart"));

            // Отменить все задачи, кроме тех, что относятся к защищенным плагинам
            Bukkit.getScheduler().getPendingTasks().forEach(task -> {
                Plugin taskOwner = task.getOwner();
                if (taskOwner != null && !protectedPlugins.contains(taskOwner.getName()) && taskOwner != this) {
                    task.cancel();
                }
            });

            // Задержка перед остановкой сервера
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
            }, 20L * gracefulDelaySeconds);
        } else {
            // Обычный рестарт
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
            }, 20L * 3); // 3 секунды
        }
    }

    /**
     * Запускает рестарт сервера из других классов
     * 
     * @param reason причина рестарта
     */
    public void restartServer(String reason) {
        logger.info("Запрос рестарта сервера. Причина: " + reason);

        // Уведомляем другие плагины через API
        if (restartAPI != null) {
            restartAPI.notifyRestartStarted();
        }

        // Выполняем рестарт
        performRestart();
    }

    // Класс для обработки событий Bukkit
    private class BukkitListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            // Добавляем игрока к боссбару, если он активен
            if (bossBar != null) {
                bossBar.addPlayer(event.getPlayer());
            }
        }
    }

    /**
     * Показывает заголовок (title) всем игрокам
     * 
     * @param secondsUntilRestart секунд до рестарта
     */
    private void showTitle(long secondsUntilRestart) {
        if (!titlesEnabled) {
            return;
        }
        
        String formattedTime = formatTime(secondsUntilRestart);
        String title = language.getMessage("messages.title-warning");
        String subtitle = language.getMessage("messages.subtitle-warning", "%time%", formattedTime);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, titleFadeIn, titleStay, titleFadeOut);
        }
    }
    
    /**
     * Запускает задачу показа строки действий (actionbar)
     * 
     * @param secondsUntilRestart секунд до рестарта
     */
    private void startActionBar(long secondsUntilRestart) {
        if (!actionbarEnabled) {
            return;
        }
        
        // Отменяем предыдущую задачу, если она существует
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }
        
        // Запускаем новую задачу
        actionbarTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long current = secondsUntilRestart - (System.currentTimeMillis() / 1000 - System.currentTimeMillis() / 1000);
                if (current <= 0) {
                    if (actionbarTask != null) {
                        actionbarTask.cancel();
                        actionbarTask = null;
                    }
                    return;
                }
                
                String formattedTime = formatTime(current);
                String message = language.getMessage("messages.actionbar-warning", "%time%", formattedTime);
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
                }
            }
        }, 0L, actionbarUpdateFrequency);
    }
    
    /**
     * Останавливает показ строки действий
     */
    private void stopActionBar() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }
    }
    
    /**
     * Проигрывает звук предупреждения всем игрокам
     * 
     * @param isFinal true если это финальное предупреждение (последние секунды)
     */
    private void playWarningSound(boolean isFinal) {
        if (!soundsEnabled) {
            return;
        }
        
        String soundName = isFinal ? finalWarningSound : warningSound;
        
        try {
            Sound sound = Sound.valueOf(soundName);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), sound, soundVolume, soundPitch);
            }
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid sound name: " + soundName);
        }
    }
    
    /**
     * Показывает визуальные эффекты вокруг игроков
     */
    private void showVisualEffects() {
        if (!visualEffectsEnabled) {
            return;
        }
        
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location loc = player.getLocation();
                
                // Пытаемся использовать новый API частиц, если не получится - используем старый API эффектов
                try {
                    Particle particle = Particle.valueOf(effectType);
                    
                    for (int i = 0; i < effectCount; i++) {
                        double x = loc.getX() + (Math.random() * 2 - 1) * effectRadius;
                        double y = loc.getY() + (Math.random() * 2) * effectRadius;
                        double z = loc.getZ() + (Math.random() * 2 - 1) * effectRadius;
                        
                        loc.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
                    }
                } catch (IllegalArgumentException e) {
                    // Если не нашли частицу, пробуем использовать эффект
                    try {
                        Effect effect = Effect.valueOf(effectType);
                        
                        for (int i = 0; i < effectCount; i++) {
                            double x = loc.getX() + (Math.random() * 2 - 1) * effectRadius;
                            double y = loc.getY() + (Math.random() * 2) * effectRadius;
                            double z = loc.getZ() + (Math.random() * 2 - 1) * effectRadius;
                            
                            loc.getWorld().playEffect(new Location(loc.getWorld(), x, y, z), effect, 0);
                        }
                    } catch (IllegalArgumentException ex) {
                        logger.warning("Invalid effect/particle type: " + effectType);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error showing visual effects: " + e.getMessage());
        }
    }
}