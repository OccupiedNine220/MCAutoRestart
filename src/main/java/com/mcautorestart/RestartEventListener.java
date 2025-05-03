package com.mcautorestart;

/**
 * Интерфейс для получения уведомлений о перезапуске сервера
 */
public interface RestartEventListener {

    /**
     * Вызывается при запланированном перезапуске
     *
     * @param minutesLeft минут до перезапуска
     */
    void onRestartScheduled(int minutesLeft);

    /**
     * Вызывается при запуске процесса перезапуска
     */
    void onRestartStarted();

    /**
     * Вызывается при отмене перезапуска
     */
    void onRestartCancelled();

    /**
     * Устанавливает имя плагина (вызывается при регистрации)
     *
     * @param pluginName имя плагина
     */
    void setPluginName(String pluginName);

    /**
     * Возвращает имя плагина
     *
     * @return имя плагина
     */
    String getPluginName();
}