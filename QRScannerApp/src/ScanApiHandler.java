import com.sun.net.httpserver.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * POST /api/scan
 * Body (JSON): { "image": "data:image/png;base64,..." }
 * Response:    { "success": true, "text": "...", "isUrl": true/false }
 *           or { "success": false, "error": "..." }
 */
public class ScanApiHandler implements HttpHandler {

    private final QRScanner    scanner;
    private final HistoryStore history;

    public ScanApiHandler(HistoryStore history) {
        this.scanner = new QRScanner();
        this.history = history;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // Extract base64 payload from data URL: "data:image/...;base64,<data>"
            String base64 = extractBase64(body);
            if (base64 == null || base64.isEmpty()) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"No image data provided\"}");
                return;
            }

            // Decode base64 → BufferedImage
            byte[] imgBytes = Base64.getDecoder().decode(base64);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imgBytes));
            if (image == null) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"Could not read image format\"}");
                return;
            }

            // ── ZXing QR Decode ─────────────────────────────────────────────
            String text  = scanner.scanFromImage(image);
            boolean isUrl = isUrl(text);

            history.add(text, isUrl);

            String json = String.format(
                "{\"success\":true,\"text\":%s,\"isUrl\":%b}",
                toJsonString(text), isUrl);
            sendJson(ex, 200, json);

        } catch (com.google.zxing.NotFoundException e) {
            sendJson(ex, 200, "{\"success\":false,\"error\":\"No QR code found in image\"}");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"success\":false,\"error\":" + toJsonString(e.getMessage()) + "}");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Extracts the raw base64 string from a data URL or plain JSON field. */
    private String extractBase64(String body) {
        // data:image/png;base64,XXXX  (inside JSON string)
        int idx = body.indexOf("base64,");
        if (idx != -1) {
            String raw = body.substring(idx + 7);
            // Strip trailing JSON chars  "}  or whitespace
            int end = raw.indexOf('"');
            return end != -1 ? raw.substring(0, end).trim() : raw.trim();
        }
        return null;
    }

    private boolean isUrl(String t) {
        if (t == null) return false;
        String s = t.trim().toLowerCase();
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("www.");
    }

    private String toJsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }

    private void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }
}
