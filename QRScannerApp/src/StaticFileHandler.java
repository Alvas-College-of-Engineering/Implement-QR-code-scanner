import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.file.*;

/**
 * Serves static files from the "web/" directory.
 * GET / → web/index.html
 */
public class StaticFileHandler implements HttpHandler {

    private final String webRoot;

    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        File file = new File(webRoot + path);

        if (!file.exists() || !file.isFile()) {
            byte[] msg = ("404 Not Found: " + path).getBytes();
            ex.sendResponseHeaders(404, msg.length);
            ex.getResponseBody().write(msg);
            ex.getResponseBody().close();
            return;
        }

        ex.getResponseHeaders().set("Content-Type", mimeType(path));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        byte[] bytes = Files.readAllBytes(file.toPath());
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private String mimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }
}
