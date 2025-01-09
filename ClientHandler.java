import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
/*import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;*/

public class ClientHandler implements Runnable {
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
            BufferedOutputStream bos = new BufferedOutputStream(clientOutput)
        ) {
            // Lire la requête HTTP
            String requestLine = reader.readLine();
            if (requestLine == null || !requestLine.startsWith("GET")) {
                System.err.println("Requête non valide reçue : " + requestLine);
                bos.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                System.err.println("Requête mal formée.");
                bos.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Construire l'URL complète
            String urlString = requestParts[1];
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "http://localhost" + urlString;
            }

            URI uri = new URI(urlString);
            String host = uri.getHost();
            int port = 80;

            // Vérifier le cache
            byte[] cachedData = CacheManager.get(urlString);
            if (cachedData != null) {
                System.out.println("Cache hit pour l'URL : " + urlString);
                bos.write(cachedData);
                bos.flush();
                return;
            }

            // Requête au serveur distant
            try (
                Socket serverSocket = new Socket(host, port);
                OutputStream serverOutput = serverSocket.getOutputStream();
                InputStream serverInput = serverSocket.getInputStream();
            ) {
                // Transmettre la requête au serveur distant
                String requestHeaders = requestLine + "\r\n" +
                                        "Host: " + host + "\r\n" +
                                        "Connection: close\r\n\r\n";
                serverOutput.write(requestHeaders.getBytes(StandardCharsets.UTF_8));
                serverOutput.flush();

                // Lire et transmettre la réponse du serveur
                ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = serverInput.read(buffer)) != -1) {
                    responseBuffer.write(buffer, 0, bytesRead);
                }

                // Stocker la réponse dans le cache
                byte[] serverResponse = responseBuffer.toByteArray();
                CacheManager.put(urlString, serverResponse);

                // Transmettre au client
                bos.write(serverResponse);
                bos.flush();
            }
        } catch (IOException | URISyntaxException e) {
            System.err.println("Erreur lors du traitement de la requête : " + e.getMessage());
            e.printStackTrace();
            try {
                clientSocket.getOutputStream().write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture de la connexion : " + e.getMessage());
            }
        }
    }
}
