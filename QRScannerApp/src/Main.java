import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws Exception {

        // Auto-find a free port starting from 9090
        int port = findFreePort(9090);

        // Shared history store
        HistoryStore history = new HistoryStore("history.txt");

        // Build HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/scan",    new ScanApiHandler(history));
        server.createContext("/api/history", new HistoryApiHandler(history));
        server.createContext("/",            new StaticFileHandler("web"));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        String url = "http://localhost:" + port;
        System.out.println();
        System.out.println("  +------------------------------------------+");
        System.out.println("  |       QR Scanner - Dynamic Web App       |");
        System.out.println("  |   Running at: " + url + "        |");
        System.out.println("  |   Press Ctrl+C to stop                   |");
        System.out.println("  +------------------------------------------+");
        System.out.println();

        // Auto-open browser
        try {
            Thread.sleep(400);
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            System.out.println("  Open your browser at: " + url);
        }
    }

    /** Scans ports starting from 'start' and returns the first available one. */
    private static int findFreePort(int start) {
        for (int port = start; port <= start + 20; port++) {
            try (ServerSocket s = new ServerSocket(port)) {
                s.setReuseAddress(true);
                return port;
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("No free port found in range " + start + "-" + (start + 20));
    }
}
