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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

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
    private final Map<String, Integer> taskStats;
    private final Map<String, Long> taskExecutionTimes;
    private int taskErrorCount;
    private final List<String> priorityCategories;
    private boolean performanceMonitoring;

    /**
     * Конструктор планировщика задач
     *
     * @param plugin основной плагин
     */
    public TaskScheduler(MCAutoRestart plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.activeTasks = new ConcurrentHashMap<>();
        this.taskStats = new ConcurrentHashMap<>();
        this.taskExecutionTimes = new ConcurrentHashMap<>();
        this.taskErrorCount = 0;
        this.priorityCategories = new ArrayList<>();
        loadConfig();
    }

    /**
     * Загрузка конфигурации планировщика
     */
    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("task_scheduler.enabled", true);
        maxConcurrentTasks = plugin.getConfig().getInt("task_scheduler.max_concurrent_tasks", 10);
        logTaskExecution = plugin.getConfig().getBoolean("task_scheduler.log_execution", true);
        performanceMonitoring = plugin.getConfig().getBoolean("task_scheduler.performance_monitoring", false);
        
        List<String> configPriorities = plugin.getConfig().getStringList("task_scheduler.priority_categories");
        if (configPriorities != null && !configPriorities.isEmpty()) {
            priorityCategories.addAll(configPriorities);
        } else {
            priorityCategories.add("restart");
            priorityCategories.add("system");
        }
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
        return scheduleTaskInternal(name, delayTicks, task, null, TaskPriority.NORMAL, false);
    }
    
    /**
     * Планирует выполнение задачи с указанным приоритетом
     *
     * @param name        имя задачи
     * @param delayTicks  задержка в тиках
     * @param task        задача для выполнения
     * @param priority    приоритет задачи
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleTask(String name, long delayTicks, Runnable task, TaskPriority priority) {
        return scheduleTaskInternal(name, delayTicks, task, null, priority, false);
    }
    
    /**
     * Планирует выполнение задачи с указанным приоритетом и категорией
     *
     * @param name        имя задачи
     * @param delayTicks  задержка в тиках
     * @param task        задача для выполнения
     * @param category    категория задачи
     * @param priority    приоритет задачи
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleTask(String name, long delayTicks, Runnable task, String category, TaskPriority priority) {
        return scheduleTaskInternal(name, delayTicks, task, category, priority, false);
    }

    /**
     * Внутренний метод для планирования задач
     */
    private String scheduleTaskInternal(String name, long delayTicks, Runnable task, 
                                      String category, TaskPriority priority, boolean isAsync) {
        if (!enabled) {
            return null;
        }
        
        // Проверка на превышение лимита задач
        if (activeTasks.size() >= maxConcurrentTasks) {
            // Если категория задачи имеет высокий приоритет, можно попытаться найти задачу с низким приоритетом для отмены
            if (category != null && priorityCategories.contains(category)) {
                boolean canceled = cancelLowPriorityTask();
                if (!canceled) {
                    logger.warning("Cannot schedule task " + name + ": maximum number of concurrent tasks reached");
                    return null;
                }
            } else if (priority == TaskPriority.HIGH) {
                boolean canceled = cancelLowPriorityTask();
                if (!canceled) {
                    logger.warning("Cannot schedule high priority task " + name + ": maximum number of concurrent tasks reached");
                    return null;
                }
            } else {
                logger.warning("Cannot schedule task " + name + ": maximum number of concurrent tasks reached");
                return null;
            }
        }

        String taskId = UUID.randomUUID().toString();
        BukkitTask bukkitTask;
        
        Runnable wrappedTask = () -> {
            long startTime = System.currentTimeMillis();
            try {
                if (logTaskExecution) {
                    logger.info("Executing " + (isAsync ? "async " : "") + "task: " + name);
                }
                task.run();
                updateTaskStats(name, true);
                
                if (performanceMonitoring) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    taskExecutionTimes.put(name, executionTime);
                    if (executionTime > 500) { // Если выполнение заняло больше 500мс, записываем предупреждение
                        logger.warning("Task " + name + " took " + executionTime + "ms to execute");
                    }
                }
            } catch (Exception e) {
                logger.warning("Error executing task " + name + ": " + e.getMessage());
                e.printStackTrace();
                updateTaskStats(name, false);
                taskErrorCount++;
            } finally {
                activeTasks.remove(taskId);
            }
        };
        
        if (isAsync) {
            bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, wrappedTask, delayTicks);
        } else {
            bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, wrappedTask, delayTicks);
        }

        ScheduledTask scheduledTask = new ScheduledTask(
                taskId,
                name,
                bukkitTask.getTaskId(),
                category,
                priority,
                LocalDateTime.now(),
                LocalDateTime.now().plusNanos(delayTicks * 50_000_000L)
        );

        activeTasks.put(taskId, scheduledTask);
        return taskId;
    }

    /**
     * Отменяет задачу с низким приоритетом для освобождения места
     * @return true если задача была отменена
     */
    private boolean cancelLowPriorityTask() {
        List<ScheduledTask> lowPriorityTasks = activeTasks.values().stream()
                .filter(task -> task.getPriority() == TaskPriority.LOW)
                .collect(Collectors.toList());
        
        if (!lowPriorityTasks.isEmpty()) {
            ScheduledTask taskToCancel = lowPriorityTasks.get(0);
            cancelTask(taskToCancel.getId());
            logger.info("Cancelled low priority task " + taskToCancel.getName() + " to make room for higher priority task");
            return true;
        }
        return false;
    }

    /**
     * Обновляет статистику выполнения задач
     * 
     * @param taskName имя задачи
     * @param success успешно ли выполнена задача
     */
    private void updateTaskStats(String taskName, boolean success) {
        String key = taskName + (success ? "_success" : "_fail");
        Integer count = taskStats.getOrDefault(key, 0);
        taskStats.put(key, count + 1);
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
        return scheduleRepeatingTask(name, delayTicks, periodTicks, task, null, TaskPriority.NORMAL);
    }
    
    /**
     * Планирует повторяющуюся задачу с категорией и приоритетом
     *
     * @param name        имя задачи
     * @param delayTicks  начальная задержка в тиках
     * @param periodTicks период повторения в тиках
     * @param task        задача для выполнения
     * @param category    категория задачи
     * @param priority    приоритет задачи
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleRepeatingTask(String name, long delayTicks, long periodTicks, 
                                       Runnable task, String category, TaskPriority priority) {
        if (!enabled || activeTasks.size() >= maxConcurrentTasks) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long startTime = System.currentTimeMillis();
            try {
                if (logTaskExecution) {
                    logger.info("Executing repeating task: " + name);
                }
                task.run();
                updateTaskStats(name, true);
                
                if (performanceMonitoring) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    taskExecutionTimes.put(name, executionTime);
                    if (executionTime > 500) {
                        logger.warning("Repeating task " + name + " took " + executionTime + "ms to execute");
                    }
                }
            } catch (Exception e) {
                logger.warning("Error executing repeating task " + name + ": " + e.getMessage());
                e.printStackTrace();
                updateTaskStats(name, false);
                taskErrorCount++;
            }
        }, delayTicks, periodTicks);

        ScheduledTask scheduledTask = new ScheduledTask(
                taskId,
                name,
                bukkitTask.getTaskId(),
                category,
                priority,
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
        return scheduleTaskInternal(name, delayTicks, task, null, TaskPriority.NORMAL, true);
    }
    
    /**
     * Планирует асинхронную задачу с указанным приоритетом и категорией
     *
     * @param name        имя задачи
     * @param delayTicks  задержка в тиках
     * @param task        задача для выполнения
     * @param category    категория задачи
     * @param priority    приоритет задачи
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleAsyncTask(String name, long delayTicks, Runnable task, 
                                   String category, TaskPriority priority) {
        return scheduleTaskInternal(name, delayTicks, task, category, priority, true);
    }
    
    /**
     * Планирует повторяющуюся асинхронную задачу
     *
     * @param name        имя задачи
     * @param delayTicks  начальная задержка в тиках
     * @param periodTicks период повторения в тиках
     * @param task        задача для выполнения
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleRepeatingAsyncTask(String name, long delayTicks, long periodTicks, Runnable task) {
        return scheduleRepeatingAsyncTask(name, delayTicks, periodTicks, task, null, TaskPriority.NORMAL);
    }
    
    /**
     * Планирует повторяющуюся асинхронную задачу с категорией и приоритетом
     *
     * @param name        имя задачи
     * @param delayTicks  начальная задержка в тиках
     * @param periodTicks период повторения в тиках
     * @param task        задача для выполнения
     * @param category    категория задачи
     * @param priority    приоритет задачи
     * @return идентификатор задачи или null, если планировщик отключен
     */
    public String scheduleRepeatingAsyncTask(String name, long delayTicks, long periodTicks, 
                                           Runnable task, String category, TaskPriority priority) {
        if (!enabled || activeTasks.size() >= maxConcurrentTasks) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long startTime = System.currentTimeMillis();
            try {
                if (logTaskExecution) {
                    logger.info("Executing async repeating task: " + name);
                }
                task.run();
                updateTaskStats(name, true);
                
                if (performanceMonitoring) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    taskExecutionTimes.put(name, executionTime);
                    if (executionTime > 500) {
                        logger.warning("Async repeating task " + name + " took " + executionTime + "ms to execute");
                    }
                }
            } catch (Exception e) {
                logger.warning("Error executing async repeating task " + name + ": " + e.getMessage());
                e.printStackTrace();
                updateTaskStats(name, false);
                taskErrorCount++;
            }
        }, delayTicks, periodTicks);

        ScheduledTask scheduledTask = new ScheduledTask(
                taskId,
                name,
                bukkitTask.getTaskId(),
                category,
                priority,
                LocalDateTime.now(),
                null // Повторяющаяся задача не имеет времени завершения
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
     * Отменяет все задачи с указанным именем
     *
     * @param taskName имя задачи
     * @return количество отмененных задач
     */
    public int cancelTasksByName(String taskName) {
        List<String> toCancel = activeTasks.entrySet().stream()
                .filter(entry -> entry.getValue().getName().equals(taskName))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        int count = 0;
        for (String taskId : toCancel) {
            if (cancelTask(taskId)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Отменяет все задачи указанной категории
     *
     * @param category категория задач
     * @return количество отмененных задач
     */
    public int cancelTasksByCategory(String category) {
        if (category == null) {
            return 0;
        }
        
        List<String> toCancel = activeTasks.entrySet().stream()
                .filter(entry -> category.equals(entry.getValue().getCategory()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        int count = 0;
        for (String taskId : toCancel) {
            if (cancelTask(taskId)) {
                count++;
            }
        }
        
        return count;
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
     * Возвращает количество ошибок в задачах
     *
     * @return количество ошибок
     */
    public int getErrorCount() {
        return taskErrorCount;
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
            
            if (task.getCategory() != null) {
                info += " [" + task.getCategory() + "]";
            }
            
            info += " (" + task.getPriority() + ")";
            
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
     * Возвращает статистику выполнения задач
     *
     * @return статистика выполнения задач
     */
    public Map<String, Integer> getTaskStats() {
        return Collections.unmodifiableMap(taskStats);
    }
    
    /**
     * Возвращает информацию о времени выполнения задач
     *
     * @return карта с временем выполнения задач
     */
    public Map<String, Long> getTaskExecutionTimes() {
        return Collections.unmodifiableMap(taskExecutionTimes);
    }
    
    /**
     * Сбрасывает статистику выполнения задач
     */
    public void resetTaskStats() {
        taskStats.clear();
        taskExecutionTimes.clear();
        taskErrorCount = 0;
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
     * Включает или выключает мониторинг производительности
     *
     * @param enabled новое состояние
     */
    public void setPerformanceMonitoring(boolean enabled) {
        this.performanceMonitoring = enabled;
        if (!enabled) {
            taskExecutionTimes.clear();
        }
    }
    
    /**
     * Добавляет категорию в список приоритетных
     *
     * @param category категория
     */
    public void addPriorityCategory(String category) {
        if (category != null && !priorityCategories.contains(category)) {
            priorityCategories.add(category);
        }
    }
    
    /**
     * Удаляет категорию из списка приоритетных
     *
     * @param category категория
     * @return true, если категория была удалена
     */
    public boolean removePriorityCategory(String category) {
        return priorityCategories.remove(category);
    }

    /**
     * Возвращает список приоритетных категорий
     *
     * @return список приоритетных категорий
     */
    public List<String> getPriorityCategories() {
        return Collections.unmodifiableList(priorityCategories);
    }

    /**
     * Приоритет задачи
     */
    public enum TaskPriority {
        LOW,
        NORMAL,
        HIGH
    }

    /**
     * Класс для хранения информации о запланированной задаче
     */
    private static class ScheduledTask {
        private final String id;
        private final String name;
        private final int bukkitTaskId;
        private final String category;
        private final TaskPriority priority;
        private final LocalDateTime creationTime;
        private final LocalDateTime executionTime;

        public ScheduledTask(String id, String name, int bukkitTaskId,
                             String category, TaskPriority priority,
                             LocalDateTime creationTime, LocalDateTime executionTime) {
            this.id = id;
            this.name = name;
            this.bukkitTaskId = bukkitTaskId;
            this.category = category;
            this.priority = priority;
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
        
        public String getCategory() {
            return category;
        }
        
        public TaskPriority getPriority() {
            return priority;
        }

        public LocalDateTime getCreationTime() {
            return creationTime;
        }

        public LocalDateTime getExecutionTime() {
            return executionTime;
        }
    }
} 