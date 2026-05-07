import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * GET    /api/history  → returns JSON array of last scans
 * DELETE /api/history  → clears history
 */
public class HistoryApiHandler implements HttpHandler {

    private final HistoryStore history;

    public HistoryApiHandler(HistoryStore history) {
        this.history = history;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

        switch (ex.getRequestMethod()) {
            case "GET"    -> sendJson(ex, 200, history.toJson());
            case "DELETE" -> { history.clear(); sendJson(ex, 200, "{\"cleared\":true}"); }
            default       -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }
}
