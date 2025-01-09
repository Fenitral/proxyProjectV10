import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;


public class CacheManager {
    private static final String CACHE_DIRECTORY = ConfigManager.get("cache.directory", "cache");
    private static final String MAPPING_FILE = ConfigManager.get("cache.mapping", "file/cacheMapping.txt");
    private static final long CACHE_TTL = ConfigManager.getInt("cache.expiration.duration", 60000);
    private static final int MAX_MEMORY_CACHE_SIZE = ConfigManager.getInt("cache.max.memory", 100); 
    private static final long MAX_MEMORY_ITEM_SIZE = ConfigManager.getLong("cache.max.memory.item", 1 * 1024 * 1024);

    // Cache en mémoire avec éviction LRU
    private static final Map<String, byte[]> memoryCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_MEMORY_CACHE_SIZE;
        }
    };

    static {
        File cacheDir = new File(CACHE_DIRECTORY);
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
        try {
            File mappingFile = new File(MAPPING_FILE);
            if (!mappingFile.exists()) {
                mappingFile.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la création du fichier de mapping : " + e.getMessage());
        }
    }

    public static synchronized byte[] get(String url) {
        String cacheFileName = getCacheFileName(url);

        // Vérification du cache en mémoire
        if (memoryCache.containsKey(cacheFileName)) {
            System.out.println("Données trouvées en mémoire pour : " + cacheFileName);
            return memoryCache.get(cacheFileName);
        }

        // Vérification sur disque
        File cacheFile = new File(CACHE_DIRECTORY, cacheFileName);
        if (cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) <= CACHE_TTL) {
            try (FileInputStream fis = new FileInputStream(cacheFile);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; // 8 Ko
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                byte[] data = bos.toByteArray();
                // Charger les données en mémoire si elles respectent les critères de taille
                if (data.length <= MAX_MEMORY_ITEM_SIZE) {
                    memoryCache.put(cacheFileName, data);
                }
                return data;
            } catch (IOException e) {
                System.err.println("Erreur lors de la lecture du cache: " + e.getMessage());
            }
        }

        return null;
    }

    public static synchronized void put(String url, byte[] data) {
        String cacheFileName = getCacheFileName(url);

        // Ajouter au cache en mémoire si les données respectent les critères
        if (data.length <= MAX_MEMORY_ITEM_SIZE) {
            memoryCache.put(cacheFileName, data);
        } else {
            System.out.println("Données trop volumineuses pour être stockées en mémoire : " + cacheFileName);
        }

        // Ajouter au cache sur disque
        File cacheFile = new File(CACHE_DIRECTORY, cacheFileName);
        System.out.println("Nom du fichier de cache créé : " + cacheFile.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            fos.write(data);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture dans le cache: " + e.getMessage());
        }

        // Mettre à jour le fichier de mapping
        updateMappingFile(url, cacheFileName);
    }

    public static synchronized void deleteAllFromUrl(String url) {
        String cacheFileName = getCacheFileName(url);

        // Supprimer du cache en mémoire
        if (memoryCache.containsKey(cacheFileName)) {
            memoryCache.remove(cacheFileName);
            System.out.println("Données supprimées du cache en mémoire pour l'URL : " + url);
        } else {
            System.out.println("Aucune donnée à supprimer du cache en mémoire pour l'URL : " + url);
        }

        // Supprimer du cache sur disque
        File cacheFile = new File(CACHE_DIRECTORY, cacheFileName);
        if (cacheFile.exists()) {
            if (cacheFile.delete()) {
                System.out.println("Fichier de cache supprimé pour l'URL : " + url);
                removeFromMappingFile(url);
            } else {
                System.err.println("Erreur lors de la suppression du fichier de cache pour l'URL : " + url);
            }
        } else {
            System.out.println("Le fichier de cache n'existe pas pour l'URL : " + url);
        }
    }

    public static synchronized void clearCache() {
        // Vider le cache en mémoire
        memoryCache.clear();
        System.out.println("cache en mémoire vidé");

        // Vider le cache sur disque
        File cacheDir = new File(CACHE_DIRECTORY);
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] cacheFiles = cacheDir.listFiles();
            if (cacheFiles != null) {
                for (File file : cacheFiles) {
                    if (file.isFile() && !file.delete()) {
                        System.err.println("Erreur lors de la suppression du fichier cache : " + file.getName());
                    }
                }
            }
        }
        System.out.println("cache en dossier vidé");

        // Vider le fichier de mapping
        try (FileWriter writer = new FileWriter(MAPPING_FILE)) {
            writer.write("");
        } catch (IOException e) {
            System.err.println("Erreur lors de la suppression du fichier de mapping : " + e.getMessage());
        }
    }

    // Méthode pour générer le nom de fichier cache à partir de l'URL
    private static String getCacheFileName(String url) {
        // Lire le fichier de mapping pour trouver le nom de fichier de cache
        try (Scanner scanner = new Scanner(new File(MAPPING_FILE))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split("=");
                if (parts.length == 2 && parts[0].equals(url)) {
                    return parts[1];
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Fichier de mapping non trouvé : " + e.getMessage());
        }

        // Si non trouvé, générer le nom de fichier de cache normalement
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur lors du calcul du nom de fichier cache", e);
        }
    }

    // Méthode pour mettre à jour le fichier de mapping
    private static void updateMappingFile(String url, String cacheFileName) {
        try (FileWriter writer = new FileWriter(MAPPING_FILE, true);
             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
            bufferedWriter.write(url + "=" + cacheFileName);
            bufferedWriter.newLine();
        } catch (IOException e) {
            System.err.println("Erreur lors de la mise à jour du fichier de mapping : " + e.getMessage());
        }
    }

    // Méthode pour supprimer une entrée du fichier de mapping    
    private static void removeFromMappingFile(String url) {
        Path tempFilePath = Paths.get(MAPPING_FILE + ".tmp");
        try (BufferedReader reader = new BufferedReader(new FileReader(MAPPING_FILE));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFilePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(url + "=")) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la suppression du fichier de mapping : " + e.getMessage());
            return; // Arrêter la méthode en cas d'erreur de lecture/écriture
        }

        // Remplacer l'ancien fichier de mapping par le nouveau
        Path originalFilePath = Paths.get(MAPPING_FILE);
        try {
            Files.move(tempFilePath, originalFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Fichier de mapping mis à jour.");
        } catch (IOException e) {
            System.err.println("Erreur lors de la mise à jour du fichier de mapping : " + e.getMessage());
            // Supprimer le fichier temporaire si la mise à jour échoue
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException ex) {
                System.err.println("Erreur lors de la suppression du fichier temporaire : " + ex.getMessage());
            }
        }
    }

    // Méthode pour lister les fichiers du cache
    public static synchronized void listCache() {
        System.out.println("Contenu du cache en hashmap :");
        memoryCache.keySet().forEach(key -> System.out.println("  - " + key));

        System.out.println("Contenu du cache dans le dossier :");
        File cacheDir = new File(CACHE_DIRECTORY);
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] cacheFiles = cacheDir.listFiles();
            if (cacheFiles != null) {
                for (File file : cacheFiles) {
                    System.out.println("  - " + file.getName());
                }
            }
        }
    }
}
