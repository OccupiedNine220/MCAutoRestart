package com.mcautorestart;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * API для интеграции с другими плагинами
 */
public class RestartAPI {

    private final MCAutoRestart plugin;
    private final Logger logger;
    private boolean apiEnabled;
    private List<Integer> notifyBeforeRestartMinutes;
    private boolean delayRequestsEnabled;
    private int maxDelayMinutes;
    private int maxDelayRequests;
    private boolean cancelRequestsEnabled;
    private int maxDailyCancelRequests;
    private List<String> allowedCancelPlugins;

    // Для отслеживания задержек и отмен
    private final Map<String, Integer> delayRequestsCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyCancelRequests = new ConcurrentHashMap<>();
    private final Map<String, RestartDelayRequest> activeDelayRequests = new ConcurrentHashMap<>();
    private LocalDateTime lastDailyCancelReset = LocalDateTime.now();

    // Для событий
    private final List<RestartEventListener> eventListeners = new ArrayList<>();

    /**
     * Конструктор API
     *
     * @param plugin основной плагин
     */
    public RestartAPI(MCAutoRestart plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfig();
    }

    /**
     * Загрузка конфигурации API
     */
    public void loadConfig() {
        apiEnabled = plugin.getConfig().getBoolean("api.enabled", true);
        notifyBeforeRestartMinutes = plugin.getConfig().getIntegerList("api.events.notify_before_restart");

        // Загрузка настроек задержки
        delayRequestsEnabled = plugin.getConfig().getBoolean("api.delay_requests.enabled", true);
        maxDelayMinutes = plugin.getConfig().getInt("api.delay_requests.max_delay", 15);
        maxDelayRequests = plugin.getConfig().getInt("api.delay_requests.max_requests", 3);

        // Загрузка настроек отмены
        cancelRequestsEnabled = plugin.getConfig().getBoolean("api.cancel_requests.enabled", false);
        maxDailyCancelRequests = plugin.getConfig().getInt("api.cancel_requests.max_daily_requests", 2);
        allowedCancelPlugins = plugin.getConfig().getStringList("api.cancel_requests.allowed_plugins");

        // Сброс счетчиков при перезагрузке
        delayRequestsCount.clear();
        if (lastDailyCancelReset.toLocalDate().isBefore(LocalDateTime.now().toLocalDate())) {
            dailyCancelRequests.clear();
            lastDailyCancelReset = LocalDateTime.now();
        }
    }

    /**
     * Проверяет, включен ли API
     *
     * @return true, если API включен
     */
    public boolean isEnabled() {
        return apiEnabled;
    }

    /**
     * Регистрирует плагин для получения уведомлений о перезапуске
     *
     * @param plugin   плагин для регистрации
     * @param listener обработчик событий
     * @return true, если регистрация прошла успешно
     */
    public boolean registerEventListener(Plugin plugin, RestartEventListener listener) {
        if (!apiEnabled) {
            return false;
        }

        listener.setPluginName(plugin.getName());
        eventListeners.add(listener);
        logger.info("Plugin " + plugin.getName() + " registered for restart events");
        return true;
    }

    /**
     * Отменяет регистрацию плагина
     *
     * @param plugin плагин для отмены регистрации
     * @return true, если отмена регистрации прошла успешно
     */
    public boolean unregisterEventListener(Plugin plugin) {
        if (!apiEnabled) {
            return false;
        }

        String pluginName = plugin.getName();
        boolean removed = eventListeners.removeIf(listener -> listener.getPluginName().equals(pluginName));

        if (removed) {
            logger.info("Plugin " + pluginName + " unregistered from restart events");
        }

        return removed;
    }

    /**
     * Запрашивает задержку перезапуска
     *
     * @param plugin       плагин, запрашивающий задержку
     * @param delayMinutes количество минут задержки
     * @param reason       причина задержки
     * @return объект запроса на задержку или null, если запрос отклонен
     */
    public RestartDelayRequest requestDelay(Plugin plugin, int delayMinutes, String reason) {
        if (!apiEnabled || !delayRequestsEnabled) {
            return null;
        }

        String pluginName = plugin.getName();

        // Проверяем, не превышено ли максимальное количество запросов
        int requestCount = delayRequestsCount.getOrDefault(pluginName, 0);
        if (requestCount >= maxDelayRequests) {
            logger.warning("Plugin " + pluginName + " exceeded maximum delay requests");
            return null;
        }

        // Проверяем, не превышено ли максимальное время задержки
        if (delayMinutes > maxDelayMinutes) {
            delayMinutes = maxDelayMinutes;
        }

        // Создаем запрос
        RestartDelayRequest request = new RestartDelayRequest(
                UUID.randomUUID().toString(),
                pluginName,
                delayMinutes,
                reason);

        // Увеличиваем счетчик запросов
        delayRequestsCount.put(pluginName, requestCount + 1);

        // Сохраняем запрос
        activeDelayRequests.put(request.getId(), request);

        logger.info("Plugin " + pluginName + " requested restart delay for " + delayMinutes + " minutes: " + reason);
        return request;
    }

    /**
     * Запрашивает отмену перезапуска
     *
     * @param plugin плагин, запрашивающий отмену
     * @param reason причина отмены
     * @return true, если запрос принят
     */
    public boolean requestCancel(Plugin plugin, String reason) {
        if (!apiEnabled || !cancelRequestsEnabled) {
            return false;
        }

        String pluginName = plugin.getName();

        // Проверяем, разрешена ли отмена для этого плагина
        if (!allowedCancelPlugins.isEmpty() && !allowedCancelPlugins.contains(pluginName)) {
            logger.warning("Plugin " + pluginName + " is not allowed to cancel restarts");
            return false;
        }

        // Проверяем, не превышено ли максимальное количество отмен за день
        int cancelCount = dailyCancelRequests.getOrDefault(pluginName, 0);
        if (cancelCount >= maxDailyCancelRequests) {
            logger.warning("Plugin " + pluginName + " exceeded maximum daily cancel requests");
            return false;
        }

        // Увеличиваем счетчик отмен
        dailyCancelRequests.put(pluginName, cancelCount + 1);

        logger.info("Plugin " + pluginName + " cancelled restart: " + reason);
        return true;
    }

    /**
     * Сбрасывает счетчик запросов на задержку
     */
    public void resetDelayRequestsCount() {
        delayRequestsCount.clear();
        activeDelayRequests.clear();
    }

    /**
     * Проверяет, есть ли активные запросы на задержку
     *
     * @return true, если есть активные запросы на задержку
     */
    public boolean hasActiveDelayRequests() {
        return !activeDelayRequests.isEmpty();
    }

    /**
     * Возвращает максимальное время задержки из всех активных запросов
     *
     * @return максимальное время задержки в минутах
     */
    public int getMaxActiveDelayMinutes() {
        if (activeDelayRequests.isEmpty()) {
            return 0;
        }

        return activeDelayRequests.values().stream()
                .mapToInt(RestartDelayRequest::getDelayMinutes)
                .max()
                .orElse(0);
    }

    /**
     * Возвращает список активных запросов на задержку
     *
     * @return список активных запросов на задержку
     */
    public List<RestartDelayRequest> getActiveDelayRequests() {
        return new ArrayList<>(activeDelayRequests.values());
    }

    /**
     * Уведомляет зарегистрированные плагины о предстоящем перезапуске
     *
     * @param minutesLeft минут до перезапуска
     */
    public void notifyBeforeRestart(int minutesLeft) {
        if (!apiEnabled || eventListeners.isEmpty()) {
            return;
        }

        // Проверяем, нужно ли отправлять уведомление для этого времени
        if (!notifyBeforeRestartMinutes.contains(minutesLeft)) {
            return;
        }

        // Отправляем уведомление всем зарегистрированным плагинам
        for (RestartEventListener listener : eventListeners) {
            try {
                listener.onRestartScheduled(minutesLeft);
            } catch (Exception e) {
                logger.warning("Error notifying plugin " + listener.getPluginName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Уведомляет зарегистрированные плагины о запуске перезапуска
     */
    public void notifyRestartStarted() {
        if (!apiEnabled || eventListeners.isEmpty()) {
            return;
        }

        // Отправляем уведомление всем зарегистрированным плагинам
        for (RestartEventListener listener : eventListeners) {
            try {
                listener.onRestartStarted();
            } catch (Exception e) {
                logger.warning("Error notifying plugin " + listener.getPluginName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Уведомляет зарегистрированные плагины об отмене перезапуска
     */
    public void notifyRestartCancelled() {
        if (!apiEnabled || eventListeners.isEmpty()) {
            return;
        }

        // Отправляем уведомление всем зарегистрированным плагинам
        for (RestartEventListener listener : eventListeners) {
            try {
                listener.onRestartCancelled();
            } catch (Exception e) {
                logger.warning("Error notifying plugin " + listener.getPluginName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Класс для хранения информации о запросе на задержку
     */
    public static class RestartDelayRequest {
        private final String id;
        private final String pluginName;
        private final int delayMinutes;
        private final String reason;
        private final LocalDateTime requestTime;

        public RestartDelayRequest(String id, String pluginName, int delayMinutes, String reason) {
            this.id = id;
            this.pluginName = pluginName;
            this.delayMinutes = delayMinutes;
            this.reason = reason;
            this.requestTime = LocalDateTime.now();
        }

        public String getId() {
            return id;
        }

        public String getPluginName() {
            return pluginName;
        }

        public int getDelayMinutes() {
            return delayMinutes;
        }

        public String getReason() {
            return reason;
        }

        public LocalDateTime getRequestTime() {
            return requestTime;
        }
    }
}