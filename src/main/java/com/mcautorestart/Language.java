package com.mcautorestart;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Language {
    private final MCAutoRestart plugin;
    private final Logger logger;
    private YamlConfiguration langConfig;
    private String currentLang;
    private final Map<String, String> fallbackMessages = new HashMap<>();

    public Language(MCAutoRestart plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentLang = plugin.getConfig().getString("language.default", "ru_rus");
        
        // Загружаем сообщения из config.yml как резервные
        loadFallbackMessages();
        
        // Загружаем языковой файл
        loadLanguage(currentLang);
    }
    
    private void loadFallbackMessages() {
        for (String key : plugin.getConfig().getConfigurationSection("messages").getKeys(false)) {
            fallbackMessages.put("messages." + key, plugin.getConfig().getString("messages." + key));
        }
    }
    
    public void loadLanguage(String lang) {
        this.currentLang = lang;
        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        
        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            logger.info("Loaded language file: " + lang + ".yml");
        } else {
            // Если файл не существует на диске, пробуем загрузить из ресурсов плагина
            InputStream inputStream = plugin.getResource("lang/" + lang + ".yml");
            if (inputStream != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                
                // Сохраняем файл из ресурсов на диск
                saveResourceIfNotExists("lang/" + lang + ".yml");
                logger.info("Loaded and saved language file from resources: " + lang + ".yml");
            } else {
                // Если файл не найден, используем настройки из config.yml
                logger.warning("Language file " + lang + ".yml not found, using fallback messages from config.yml");
                langConfig = new YamlConfiguration();
            }
        }
    }
    
    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        
        // Создаем директории, если они не существуют
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        
        if (!file.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to save resource: " + resourcePath, e);
            }
        }
    }
    
    public String getMessage(String path, String... replacements) {
        String message;
        
        // Пытаемся получить сообщение из языкового файла
        if (langConfig != null && langConfig.contains(path)) {
            message = langConfig.getString(path);
        } else {
            // Если не удалось, используем резервные сообщения
            message = fallbackMessages.getOrDefault(path, "Missing message: " + path);
        }
        
        // Если путь для сообщения содержит prefix, добавляем его
        if (!path.equals("messages.prefix") && path.startsWith("messages.")) {
            String prefix = getMessage("messages.prefix");
            message = prefix + message;
        }
        
        // Заменяем плейсхолдеры
        if (replacements != null && replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        
        // Преобразуем цветовые коды
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String getCurrentLang() {
        return currentLang;
    }
} 