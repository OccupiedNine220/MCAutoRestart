package com.mcautorestart;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    
    @Override
    public void onEnable() {
        logger = getLogger();
        saveDefaultConfig();
        
        // Инициализация языкового модуля
        language = new Language(this);
        
        // Загрузка конфигурации
        loadConfig();
        
        // Запуск задачи рестарта, если включено
        if (restartEnabled) {
            scheduleNextRestart();
        }
        
        logger.info("MCAutoRestart включен. Рестарты " + 
                  (restartEnabled ? "активированы" : "отключены") + 
                  ". Режим: " + restartMode);
    }
    
    @Override
    public void onDisable() {
        if (restartTask != null) {
            restartTask.cancel();
        }
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
                logger.warning("Неверный формат времени в конфигурации: " + timeStr);
            }
        }
        
        // Загружаем настройки оповещений
        warningsEnabled = config.getBoolean("notifications.warnings.enabled", true);
        maxMinutesBefore = config.getInt("notifications.warnings.max_minutes_before", 60);
        
        // Загружаем времена предупреждений
        warningMinutes = config.getIntegerList("notifications.warnings.minutes");
        warningSeconds = config.getIntegerList("notifications.warnings.seconds");
        
        // Если списки пустые, устанавливаем значения по умолчанию
        if (warningMinutes.isEmpty()) {
            warningMinutes = Arrays.asList(60, 30, 15, 10, 5, 4, 3, 2, 1);
        }
        
        if (warningSeconds.isEmpty()) {
            warningSeconds = Arrays.asList(30, 15, 10, 5, 4, 3, 2, 1);
        }
        
        // Фильтруем предупреждения по максимальному времени
        warningMinutes = warningMinutes.stream()
                                      .filter(min -> min <= maxMinutesBefore)
                                      .collect(Collectors.toList());
    }
    
    private void saveRestartState() {
        config.set("restart-enabled", restartEnabled);
        saveConfig();
    }
    
    private void scheduleNextRestart() {
        // Отменяем предыдущую задачу, если она есть
        if (restartTask != null) {
            restartTask.cancel();
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRestart;
        
        if (restartMode.equalsIgnoreCase("interval")) {
            // Интервальный режим - рестарт каждые X часов
            
            // Определяем следующее время перезапуска
            int currentHour = now.getHour();
            int targetHour = (currentHour / intervalHours) * intervalHours;
            
            if (now.getHour() == targetHour && now.getMinute() >= intervalMinutes) {
                // Если мы уже прошли время перезапуска в текущем интервале, переходим к следующему
                targetHour = (targetHour + intervalHours) % 24;
            }
            
            nextRestart = now.withHour(targetHour)
                             .withMinute(intervalMinutes)
                             .withSecond(0)
                             .withNano(0);
            
            // Если расчётное время рестарта в прошлом, добавляем интервал
            if (nextRestart.isBefore(now)) {
                nextRestart = nextRestart.plusHours(intervalHours);
            }
            
        } else {
            // Фиксированный режим - рестарт в указанное время
            
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
                return;
            }
            
            // Создаем DateTime для следующего рестарта
            nextRestart = now.withHour(closestTime.getHour())
                             .withMinute(closestTime.getMinute())
                             .withSecond(0)
                             .withNano(0);
            
            // Если расчётное время рестарта в прошлом, добавляем день
            if (nextRestart.isBefore(now)) {
                nextRestart = nextRestart.plusDays(1);
            }
        }
        
        // Рассчитываем задержку до следующего рестарта
        long delaySeconds = now.until(nextRestart, ChronoUnit.SECONDS);
        
        logger.info("Следующий рестарт запланирован на " + nextRestart + 
                  " (через " + formatTime(delaySeconds) + ")");
        
        // Планируем предупреждения только если они включены
        if (warningsEnabled) {
            // Планируем предупреждения перед рестартом (в минутах)
            for (int warningTime : warningMinutes) {
                long warningDelaySeconds = delaySeconds - (warningTime * 60);
                if (warningDelaySeconds > 0) {
                    int finalWarningTime = warningTime;
                    Bukkit.getScheduler().runTaskLater(this, () -> 
                        Bukkit.broadcastMessage(language.getMessage("messages.warning-minutes", "%time%", String.valueOf(finalWarningTime))), 
                        warningDelaySeconds * 20L);
                }
            }
            
            // Предупреждения в последнюю минуту (в секундах)
            for (int sec : warningSeconds) {
                long warningDelaySeconds = delaySeconds - sec;
                if (warningDelaySeconds > 0) {
                    int finalSec = sec;
                    Bukkit.getScheduler().runTaskLater(this, () -> 
                        Bukkit.broadcastMessage(language.getMessage("messages.warning-seconds", "%time%", String.valueOf(finalSec))), 
                        warningDelaySeconds * 20L);
                }
            }
        }
        
        // Планируем задачу рестарта
        restartTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.broadcastMessage(language.getMessage("messages.restart"));
            
            // Задержка перед остановкой сервера, чтобы сообщение успело отправиться
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
            }, 20L); // 1 секунда
            
        }, delaySeconds * 20L);
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
                sender.sendMessage(language.getMessage("messages.disabled"));
                break;
                
            case "reload":
                // Перезагрузка конфигурации
                reloadConfig();
                loadConfig();
                
                // Перезагрузка языковых файлов
                String langCode = getConfig().getString("language.default", "ru_rus");
                language.loadLanguage(langCode);
                
                // Перезапуск задачи рестарта, если включено
                if (restartEnabled) {
                    scheduleNextRestart();
                }
                
                sender.sendMessage(language.getMessage("messages.reload-complete"));
                break;
                
            case "status":
                showStatus(sender);
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
                                sender.sendMessage(ChatColor.RED + "Интервал должен быть от 1 до 24 часов.");
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
                            
                            sender.sendMessage(language.getMessage("messages.interval-set", "%hours%", String.valueOf(hours)));
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
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(language.getMessage("messages.restarting-now"));
                    
                    Bukkit.broadcastMessage(language.getMessage("messages.restart"));
                    
                    // Задержка перед остановкой сервера
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
                    }, 20L * 3); // 3 секунды
                } else {
                    sender.sendMessage(language.getMessage("messages.confirm-restart", 
                                       "%command%", "/autorestart now confirm"));
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
            int currentHour = now.getHour();
            int targetHour = (currentHour / intervalHours) * intervalHours;
            
            if (now.getHour() == targetHour && now.getMinute() >= intervalMinutes) {
                targetHour = (targetHour + intervalHours) % 24;
            }
            
            nextRestart = now.withHour(targetHour)
                             .withMinute(intervalMinutes)
                             .withSecond(0)
                             .withNano(0);
            
            if (nextRestart.isBefore(now)) {
                nextRestart = nextRestart.plusHours(intervalHours);
            }
        } else {
            // Фиксированный режим
            LocalTime currentTime = now.toLocalTime();
            LocalTime closestTime = null;
            long minDiff = Long.MAX_VALUE;
            
            for (LocalTime restartTime : fixedRestartTimes) {
                long seconds = currentTime.until(restartTime, ChronoUnit.SECONDS);
                
                if (seconds < 0) {
                    seconds += 24 * 60 * 60;
                }
                
                if (seconds < minDiff) {
                    minDiff = seconds;
                    closestTime = restartTime;
                }
            }
            
            if (closestTime == null) {
                sender.sendMessage(ChatColor.RED + "Ошибка: времена рестарта не указаны.");
                return;
            }
            
            nextRestart = now.withHour(closestTime.getHour())
                             .withMinute(closestTime.getMinute())
                             .withSecond(0)
                             .withNano(0);
            
            if (nextRestart.isBefore(now)) {
                nextRestart = nextRestart.plusDays(1);
            }
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
    }
} 