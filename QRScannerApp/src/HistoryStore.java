import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory history store with optional file persistence.
 */
public class HistoryStore {

    private static final int MAX = 10;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final String filePath;

    public HistoryStore(String filePath) {
        this.filePath = filePath;
        loadFromFile();
    }

    public synchronized void add(String text, boolean isUrl) {
        // Deduplicate most-recent
        if (!entries.isEmpty() && entries.get(0).text.equals(text)) return;

        String time = LocalTime.now().format(FMT);
        entries.add(0, new Entry(text, isUrl, time));
        if (entries.size() > MAX) entries.remove(entries.size() - 1);
        saveToFile();
    }

    public synchronized void clear() {
        entries.clear();
        saveToFile();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"text\":%s,\"isUrl\":%b,\"time\":%s}",
                jsonStr(e.text), e.isUrl, jsonStr(e.time)));
        }
        return sb.append("]").toString();
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    private void saveToFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            for (Entry e : entries)
                pw.println(e.isUrl + "|" + e.time + "|" + e.text);
        } catch (IOException ignored) {}
    }

    private void loadFromFile() {
        try {
            File f = new File(filePath);
            if (!f.exists()) return;
            List<String> lines = Files.readAllLines(f.toPath());
            for (String line : lines) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3)
                    entries.add(new Entry(parts[2], Boolean.parseBoolean(parts[0]), parts[1]));
            }
        } catch (IOException ignored) {}
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static class Entry {
        String text; boolean isUrl; String time;
        Entry(String text, boolean isUrl, String time) {
            this.text = text; this.isUrl = isUrl; this.time = time;
        }
    }
}
