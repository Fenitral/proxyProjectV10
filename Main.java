public class Main {
    public static void main(String[] args) {

        String ip = ConfigManager.get("proxy.ip", "localhost");
        int port = ConfigManager.getInt("proxy.port", 9000); 

        ProxyServer server = new ProxyServer(ip, port);
        server.start();
    }
}
