package com.mcautorestart;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Класс для обработки условных рестартов сервера
 */
public class ConditionalRestart {

    private final MCAutoRestart plugin;
    private final Logger logger;

    private boolean enabled;
    private int checkIntervalSeconds;
    private int minIntervalBetweenRestartsMinutes;
    private boolean requireAllConditions;

    // Настройки проверки TPS
    private boolean tpsCheckEnabled;
    private double minTpsValue;
    private int tpsDurationSeconds;

    // Настройки проверки памяти
    private boolean memoryCheckEnabled;
    private int maxMemoryUsagePercent;
    private int memoryDurationSeconds;

    // Настройки проверки онлайна
    private boolean playerCountCheckEnabled;
    private int maxPlayerCount;
    private int playerCountDurationSeconds;
    private boolean skipIfEmpty;

    // Состояние проверок
    private LocalDateTime lastRestartTime = LocalDateTime.now().minusHours(1);
    private BukkitTask checkTask;
    private final Map<String, ConditionState> conditionStates = new HashMap<>();

    /**
     * Конструктор для класса условных рестартов
     *
     * @param plugin основной плагин
     */
    public ConditionalRestart(MCAutoRestart plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfig();

        // Инициализация состояний проверок
        conditionStates.put("tps", new ConditionState());
        conditionStates.put("memory", new ConditionState());
        conditionStates.put("player_count", new ConditionState());
    }

    /**
     * Загрузка конфигурации
     */
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("conditional_restart.enabled", false);
        checkIntervalSeconds = plugin.getConfig().getInt("conditional_restart.check_interval", 60);
        minIntervalBetweenRestartsMinutes = plugin.getConfig()
                .getInt("conditional_restart.min_interval_between_restarts", 30);
        requireAllConditions = plugin.getConfig().getBoolean("conditional_restart.require_all_conditions", false);

        // Загрузка настроек проверки TPS
        tpsCheckEnabled = plugin.getConfig().getBoolean("conditional_restart.tps.enabled", false);
        minTpsValue = plugin.getConfig().getDouble("conditional_restart.tps.min_value", 15.0);
        tpsDurationSeconds = plugin.getConfig().getInt("conditional_restart.tps.duration", 300);

        // Загрузка настроек проверки памяти
        memoryCheckEnabled = plugin.getConfig().getBoolean("conditional_restart.memory.enabled", false);
        maxMemoryUsagePercent = plugin.getConfig().getInt("conditional_restart.memory.max_usage_percent", 90);
        memoryDurationSeconds = plugin.getConfig().getInt("conditional_restart.memory.duration", 300);

        // Загрузка настроек проверки онлайна
        playerCountCheckEnabled = plugin.getConfig().getBoolean("conditional_restart.player_count.enabled", false);
        maxPlayerCount = plugin.getConfig().getInt("conditional_restart.player_count.max_players", 3);
        playerCountDurationSeconds = plugin.getConfig().getInt("conditional_restart.player_count.duration", 600);
        skipIfEmpty = plugin.getConfig().getBoolean("conditional_restart.player_count.skip_if_empty", true);
    }

    /**
     * Запуск проверок условных рестартов
     */
    public void startChecking() {
        if (!enabled) {
            return;
        }

        // Отменяем текущую задачу, если она существует
        stopChecking();

        // Запускаем новую задачу
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkConditions, 20L, checkIntervalSeconds * 20L);
        logger.info("Conditional restart checks started with interval " + checkIntervalSeconds + " seconds");
    }

    /**
     * Остановка проверок условных рестартов
     */
    public void stopChecking() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }

        // Сбрасываем состояния проверок
        resetConditionStates();
    }

    /**
     * Проверка условий для рестарта
     */
    private void checkConditions() {
        if (!enabled) {
            return;
        }

        // Проверяем, прошло ли минимальное время с последнего рестарта
        LocalDateTime now = LocalDateTime.now();
        long minutesSinceLastRestart = java.time.temporal.ChronoUnit.MINUTES.between(lastRestartTime, now);

        if (minutesSinceLastRestart < minIntervalBetweenRestartsMinutes) {
            return;
        }

        // Проверка TPS
        if (tpsCheckEnabled) {
            checkTps();
        }

        // Проверка памяти
        if (memoryCheckEnabled) {
            checkMemory();
        }

        // Проверка онлайна
        if (playerCountCheckEnabled) {
            checkPlayerCount();
        }

        // Проверяем, нужно ли выполнить рестарт
        boolean shouldRestart;

        if (requireAllConditions) {
            // Все условия должны выполняться
            shouldRestart = tpsCheckEnabled && conditionStates.get("tps").isConditionMet()
                    && memoryCheckEnabled && conditionStates.get("memory").isConditionMet()
                    && playerCountCheckEnabled && conditionStates.get("player_count").isConditionMet();
        } else {
            // Достаточно одного условия
            shouldRestart = (tpsCheckEnabled && conditionStates.get("tps").isConditionMet())
                    || (memoryCheckEnabled && conditionStates.get("memory").isConditionMet())
                    || (playerCountCheckEnabled && conditionStates.get("player_count").isConditionMet());
        }

        if (shouldRestart) {
            logger.info("Conditional restart triggered by conditions");
            performRestart();
        }
    }

    /**
     * Проверка TPS сервера
     */
    private void checkTps() {
        double tps = getTps();
        ConditionState state = conditionStates.get("tps");

        if (tps < minTpsValue) {
            if (state.startTime == null) {
                state.startTime = LocalDateTime.now();
            }

            long durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(state.startTime, LocalDateTime.now());

            if (durationSeconds >= tpsDurationSeconds) {
                state.conditionMet = true;
                logger.info("TPS condition met: " + tps + " (threshold: " + minTpsValue + ", duration: "
                        + durationSeconds + "s)");
            }
        } else {
            state.reset();
        }
    }

    /**
     * Проверка использования памяти
     */
    private void checkMemory() {
        int memoryUsage = getMemoryUsagePercent();
        ConditionState state = conditionStates.get("memory");

        if (memoryUsage > maxMemoryUsagePercent) {
            if (state.startTime == null) {
                state.startTime = LocalDateTime.now();
            }

            long durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(state.startTime, LocalDateTime.now());

            if (durationSeconds >= memoryDurationSeconds) {
                state.conditionMet = true;
                logger.info("Memory condition met: " + memoryUsage + "% (threshold: " + maxMemoryUsagePercent
                        + "%, duration: " + durationSeconds + "s)");
            }
        } else {
            state.reset();
        }
    }

    /**
     * Проверка количества игроков
     */
    private void checkPlayerCount() {
        int playerCount = Bukkit.getOnlinePlayers().size();
        ConditionState state = conditionStates.get("player_count");

        // Если сервер пустой и настроено пропускать пустой сервер
        if (playerCount == 0 && skipIfEmpty) {
            state.reset();
            return;
        }

        if (playerCount <= maxPlayerCount) {
            if (state.startTime == null) {
                state.startTime = LocalDateTime.now();
            }

            long durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(state.startTime, LocalDateTime.now());

            if (durationSeconds >= playerCountDurationSeconds) {
                state.conditionMet = true;
                logger.info("Player count condition met: " + playerCount + " (threshold: " + maxPlayerCount
                        + ", duration: " + durationSeconds + "s)");
            }
        } else {
            state.reset();
        }
    }

    /**
     * Выполнение рестарта
     */
    private void performRestart() {
        // Обновляем время последнего рестарта
        lastRestartTime = LocalDateTime.now();

        // Сбрасываем состояния проверок
        resetConditionStates();

        // Запускаем рестарт
        plugin.restartServer("conditional");
    }

    /**
     * Сброс состояний всех проверок
     */
    private void resetConditionStates() {
        conditionStates.values().forEach(ConditionState::reset);
    }

    /**
     * Получение текущего TPS сервера
     * Использует рефлексию для доступа к внутренним данным Bukkit
     *
     * @return текущий TPS сервера
     */
    private double getTps() {
        try {
            Object serverInstance = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Field tpsField = serverInstance.getClass().getField("recentTps");
            double[] tps = (double[]) tpsField.get(serverInstance);
            return tps[0]; // Берем TPS за последнюю минуту
        } catch (Exception e) {
            logger.warning("Failed to get server TPS: " + e.getMessage());
            return 20.0; // Возвращаем максимальный TPS, если не удалось получить
        }
    }

    /**
     * Получение процента использования памяти
     *
     * @return процент использования памяти
     */
    private int getMemoryUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        return (int) ((usedMemory * 100) / maxMemory);
    }

    /**
     * Возвращает статус условных рестартов
     *
     * @return строка состояния для вывода
     */
    public String getStatus() {
        StringBuilder status = new StringBuilder("Conditional restart: " + (enabled ? "enabled" : "disabled") + "\n");

        if (enabled) {
            status.append("Checking interval: ").append(checkIntervalSeconds).append(" seconds\n");
            status.append("Min. interval between restarts: ").append(minIntervalBetweenRestartsMinutes)
                    .append(" minutes\n");
            status.append("Require all conditions: ").append(requireAllConditions).append("\n");
            status.append("TPS check: ").append(tpsCheckEnabled ? "enabled" : "disabled");

            if (tpsCheckEnabled) {
                status.append(" (min: ").append(minTpsValue).append(", duration: ").append(tpsDurationSeconds)
                        .append("s)");
                ConditionState tpsState = conditionStates.get("tps");
                if (tpsState.startTime != null) {
                    long duration = java.time.temporal.ChronoUnit.SECONDS.between(tpsState.startTime,
                            LocalDateTime.now());
                    status.append(" - Current: ").append(duration).append("s");
                    if (tpsState.conditionMet) {
                        status.append(" [TRIGGERED]");
                    }
                }
            }

            status.append("\n");
            status.append("Memory check: ").append(memoryCheckEnabled ? "enabled" : "disabled");

            if (memoryCheckEnabled) {
                status.append(" (max: ").append(maxMemoryUsagePercent).append("%, duration: ")
                        .append(memoryDurationSeconds).append("s)");
                ConditionState memoryState = conditionStates.get("memory");
                if (memoryState.startTime != null) {
                    long duration = java.time.temporal.ChronoUnit.SECONDS.between(memoryState.startTime,
                            LocalDateTime.now());
                    status.append(" - Current: ").append(duration).append("s");
                    if (memoryState.conditionMet) {
                        status.append(" [TRIGGERED]");
                    }
                }
            }

            status.append("\n");
            status.append("Player count check: ").append(playerCountCheckEnabled ? "enabled" : "disabled");

            if (playerCountCheckEnabled) {
                status.append(" (max: ").append(maxPlayerCount).append(", duration: ")
                        .append(playerCountDurationSeconds).append("s)");
                ConditionState playerState = conditionStates.get("player_count");
                if (playerState.startTime != null) {
                    long duration = java.time.temporal.ChronoUnit.SECONDS.between(playerState.startTime,
                            LocalDateTime.now());
                    status.append(" - Current: ").append(duration).append("s");
                    if (playerState.conditionMet) {
                        status.append(" [TRIGGERED]");
                    }
                }
            }
        }

        return status.toString();
    }

    /**
     * Установка состояния условных рестартов
     *
     * @param enabled новое состояние
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (enabled) {
            startChecking();
        } else {
            stopChecking();
        }

        // Сохраняем настройку в конфигурацию
        plugin.getConfig().set("conditional_restart.enabled", enabled);
        plugin.saveConfig();

        logger.info("Conditional restart " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Возвращает, включены ли условные рестарты
     *
     * @return true, если условные рестарты включены
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Класс для хранения состояния условия
     */
    private static class ConditionState {
        private LocalDateTime startTime;
        private boolean conditionMet;

        public ConditionState() {
            reset();
        }

        public void reset() {
            startTime = null;
            conditionMet = false;
        }

        public boolean isConditionMet() {
            return conditionMet;
        }
    }
}