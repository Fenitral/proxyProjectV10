import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigManager {
    private static final Properties properties = new Properties();

    static {
        String configFilePath = System.getProperty("config.file", "file/config.properties");
        try (FileInputStream input = new FileInputStream(configFilePath)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Erreur : Impossible de charger le fichier de configuration ("
                    + configFilePath + ") : " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static void configureCacheCleaner() {
        String unit = ConfigManager.get("cache.expiration.unit", "MIN");
        long duration = ConfigManager.getLong("cache.expiration.duration", 1800000);

        TimeUnit timeUnit;
        switch (unit.toUpperCase()) {
            case "SEC":
                timeUnit = TimeUnit.SECONDS;
                break;
            case "MIN":
                timeUnit = TimeUnit.MINUTES;
                duration = duration / 60000;
                break;
            case "HEURE":
                timeUnit = TimeUnit.HOURS;
                duration = duration / 3600000;
                break;
            default:
                System.err.println("Unité inconnue pour cache.expiration.unit : " + unit + ". Utilisation de MINUTES par défaut.");
                timeUnit = TimeUnit.MINUTES;
                duration = duration / 60000;
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Nettoyage automatique du cache terminé.");
            CacheManager.clearCache();
        }, duration, duration, timeUnit);
    }

}
