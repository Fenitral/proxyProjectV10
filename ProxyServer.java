import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProxyServer {
    private int port;
    private String ipAddress;

    public ProxyServer(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(ipAddress))) {
            System.out.println("Serveur proxy IP: " + ipAddress + ", Port: " + port);

            new Thread(this::commandes).start();

            ConfigManager.configureCacheCleaner();
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("client connecté: " + clientSocket.getInetAddress());

                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("serveur non initialisé: " + e.getMessage());
        }
    }

    private void commandes() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String command = scanner.nextLine().trim();
            String[] commandParts = command.split(" ", 2);

            switch (commandParts[0].toLowerCase()) {
                case "exit":
                    System.out.println("arrêt du serveur");
                    System.exit(0);
                    break;
                    
                case "pat":
                    System.out.println("La cavalerie est là !");
                    break;

                case "fenitra":
                    System.out.println("Je suis une scout !");
                    break;

                case "clear":
                    CacheManager.clearCache();
                    System.out.println("cache a été vidé en totalité");
                    break;

                case "ls":
                    CacheManager.listCache();
                    break;

                case "delete":
                    if (commandParts.length < 2) {
                        System.out.println("veuillez spécifier l'URL à supprimer du cache.");
                    } else {
                        String urlToDelete = commandParts[1];
                        CacheManager.deleteAllFromUrl(urlToDelete);
                    }
                    break;
                default:
                    System.out.println("commande inconnue");
                    break;
            }
        }
    }
}
