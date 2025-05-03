package com.mcautorestart;

/**
 * Базовый класс для реализации слушателя событий перезапуска
 */
public abstract class BaseRestartEventListener implements RestartEventListener {

    private String pluginName;

    /**
     * Создает новый слушатель с указанным именем плагина
     *
     * @param pluginName имя плагина
     */
    public BaseRestartEventListener(String pluginName) {
        this.pluginName = pluginName;
    }

    /**
     * Создает новый слушатель без указания имени плагина
     */
    public BaseRestartEventListener() {
        this.pluginName = "Unknown";
    }

    @Override
    public void onRestartScheduled(int minutesLeft) {
        // Реализация по умолчанию пустая
    }

    @Override
    public void onRestartStarted() {
        // Реализация по умолчанию пустая
    }

    @Override
    public void onRestartCancelled() {
        // Реализация по умолчанию пустая
    }

    @Override
    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }
}