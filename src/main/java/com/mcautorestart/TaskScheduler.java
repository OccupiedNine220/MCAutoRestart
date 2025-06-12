package com.mcautorestart;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Планировщик задач для MCAutoRestart
 * Позволяет создавать, отслеживать и управлять задачами
 */
public class TaskScheduler {

    private final MCAutoRestart plugin;
    private final Logger logger;
    private final Map<String, ScheduledTask> activeTasks;
    private boolean enabled;
    private int maxConcurrentTasks;
    private boolean logTaskExecution;

    /**
     * Конструктор планировщика задач
     *
     * @param plugin основной плагин
     */
    public TaskScheduler(MCAutoRestart plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.activeTasks = new ConcurrentHashMap<>();
        loadConfig();
    }

    /**
     * Загрузка конфигурации планировщика
     */
    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("task_scheduler.enabled", true);
        maxConcurrentTasks = plugin.getConfig().getInt("task_scheduler.max_concurrent_tasks", 10);
        logTaskExecution = plugin.getConfig().getBoolean("task_scheduler.log_execution", true);
    }

    /**
     * Планирует выполнение задачи через указанное количество тиков
     *
     * @param name        имя задачи
     * @param delayTicks  задержка в тиках
     * @param task        задача для выполнения
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleTask(String name, long delayTicks, Runnable task) {
        if (!enabled || activeTasks.size() >= maxConcurrentTasks) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (logTaskExecution) {
                    logger.info("Executing scheduled task: " + name);
                }
                task.run();
            } catch (Exception e) {
                logger.warning("Error executing task " + name + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                activeTasks.remove(taskId);
            }
        }, delayTicks);

        ScheduledTask scheduledTask = new ScheduledTask(
                taskId,
                name,
                bukkitTask.getTaskId(),
                LocalDateTime.now(),
                LocalDateTime.now().plusNanos(delayTicks * 50_000_000L)
        );

        activeTasks.put(taskId, scheduledTask);
        return taskId;
    }

    /**
     * Планирует повторяющуюся задачу
     *
     * @param name        имя задачи
     * @param delayTicks  начальная задержка в тиках
     * @param periodTicks период повторения в тиках
     * @param task        задача для выполнения
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleRepeatingTask(String name, long delayTicks, long periodTicks, Runnable task) {
        if (!enabled || activeTasks.size() >= maxConcurrentTasks) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (logTaskExecution) {
                    logger.info("Executing repeating task: " + name);
                }
                task.run();
            } catch (Exception e) {
                logger.warning("Error executing repeating task " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        }, delayTicks, periodTicks);

        ScheduledTask scheduledTask = new ScheduledTask(
                taskId,
                name,
                bukkitTask.getTaskId(),
                LocalDateTime.now(),
                null // Повторяющаяся задача не имеет времени завершения
        );

        activeTasks.put(taskId, scheduledTask);
        return taskId;
    }

    /**
     * Планирует асинхронную задачу
     *
     * @param name        имя задачи
     * @param delayTicks  задержка в тиках
     * @param task        задача для выполнения
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleAsyncTask(String name, long delayTicks, Runnable task) {
        if (!enabled || activeTasks.size() >= maxConcurrentTasks) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (logTaskExecution) {
                    logger.info("Executing async task: " + name);
                }
                task.run();
            } catch (Exception e) {
                logger.warning("Error executing async task " + name + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                activeTasks.remove(taskId);
            }
        }, delayTicks);

        ScheduledTask scheduledTask = new ScheduledTask(
                taskId,
                name,
                bukkitTask.getTaskId(),
                LocalDateTime.now(),
                LocalDateTime.now().plusNanos(delayTicks * 50_000_000L)
        );

        activeTasks.put(taskId, scheduledTask);
        return taskId;
    }

    /**
     * Отменяет выполнение задачи по идентификатору
     *
     * @param taskId идентификатор задачи
     * @return true, если задача была отменена
     */
    public boolean cancelTask(String taskId) {
        ScheduledTask task = activeTasks.get(taskId);
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task.getBukkitTaskId());
            activeTasks.remove(taskId);
            return true;
        }
        return false;
    }

    /**
     * Отменяет все активные задачи
     */
    public void cancelAllTasks() {
        for (ScheduledTask task : activeTasks.values()) {
            Bukkit.getScheduler().cancelTask(task.getBukkitTaskId());
        }
        activeTasks.clear();
    }

    /**
     * Возвращает количество активных задач
     *
     * @return количество активных задач
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    /**
     * Возвращает информацию о всех активных задачах
     *
     * @return карта с информацией о задачах
     */
    public Map<String, String> getTasksInfo() {
        Map<String, String> tasksInfo = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        for (Map.Entry<String, ScheduledTask> entry : activeTasks.entrySet()) {
            ScheduledTask task = entry.getValue();
            String info = task.getName();
            
            if (task.getExecutionTime() != null) {
                LocalDateTime now = LocalDateTime.now();
                Duration timeLeft = Duration.between(now, task.getExecutionTime());
                if (!timeLeft.isNegative()) {
                    info += " (выполнится через " + formatDuration(timeLeft) + ")";
                }
            }
            
            tasksInfo.put(entry.getKey(), info);
        }
        
        return tasksInfo;
    }

    /**
     * Форматирует продолжительность в читаемый вид
     *
     * @param duration продолжительность
     * @return отформатированная строка
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return minutes + " мин " + seconds + " сек";
        } else {
            return seconds + " сек";
        }
    }

    /**
     * Проверяет, включен ли планировщик задач
     *
     * @return true, если планировщик включен
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Включает или выключает планировщик задач
     *
     * @param enabled новое состояние
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            cancelAllTasks();
        }
    }

    /**
     * Класс для хранения информации о запланированной задаче
     */
    private static class ScheduledTask {
        private final String id;
        private final String name;
        private final int bukkitTaskId;
        private final LocalDateTime creationTime;
        private final LocalDateTime executionTime;

        public ScheduledTask(String id, String name, int bukkitTaskId, 
                            LocalDateTime creationTime, LocalDateTime executionTime) {
            this.id = id;
            this.name = name;
            this.bukkitTaskId = bukkitTaskId;
            this.creationTime = creationTime;
            this.executionTime = executionTime;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getBukkitTaskId() {
            return bukkitTaskId;
        }

        public LocalDateTime getCreationTime() {
            return creationTime;
        }

        public LocalDateTime getExecutionTime() {
            return executionTime;
        }
    }
} 