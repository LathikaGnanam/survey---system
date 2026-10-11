import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Main {
    static class Question implements Serializable {
        String type, text; List<String> options = new ArrayList<>();
    }
    static class Survey implements Serializable {
        int id; String title, description;
        List<Question> questions = new ArrayList<>();
        List<List<String>> responses = new ArrayList<>();
    }

    static List<Survey> surveys = new ArrayList<>();
    static int nextId = 1;
    static final String ADMIN_USER = "admin", ADMIN_PASS = "admin123";
    static final String TOKEN = UUID.randomUUID().toString();
    static final Path DB = Paths.get("data.ser");
    static final Path WEB = Paths.get("public").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        load();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/", ex -> {
            try { api(ex); }
            catch (Exception e) { send(ex, 500, "application/json", "{\"error\":\"Server error\"}"); }
        });
        server.createContext("/", Main::files);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Running at http://localhost:8080  |  Admin: /admin.html (admin / admin123)");
    }

    // ---------- API ----------
    static synchronized void api(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath(), m = ex.getRequestMethod();
        Map<String, String> q = params(ex.getRequestURI().getRawQuery());
        Map<String, String> f = m.equals("POST")
            ? params(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)) : new HashMap<>();
        String J = "application/json";

        if (p.equals("/api/surveys") && m.equals("GET")) {
            send(ex, 200, J, surveys.stream().map(Main::surveyJson).collect(Collectors.joining(",", "[", "]")));
        } else if (p.equals("/api/respond") && m.equals("POST")) {
            Survey s = find(q.get("id"));
            if (s == null) { send(ex, 404, J, err("Survey not found")); return; }
            List<String> row = new ArrayList<>();
            for (int i = 0; i < s.questions.size(); i++) {
                Question qu = s.questions.get(i);
                String a = f.getOrDefault("q" + i, "").trim();
                if (!qu.type.equals("text") && a.isEmpty()) { send(ex, 400, J, err("Please answer all rating and choice questions")); return; }
                if (qu.type.equals("rating") && !a.matches("[1-5]")) { send(ex, 400, J, err("Rating must be 1 to 5")); return; }
                row.add(a);
            }
            s.responses.add(row); save();
            send(ex, 200, J, "{\"ok\":true}");
        } else if (p.equals("/api/login") && m.equals("POST")) {
            if (ADMIN_USER.equals(f.get("username")) && ADMIN_PASS.equals(f.get("password"))) {
                ex.getResponseHeaders().add("Set-Cookie", "admin=" + TOKEN + "; Path=/; HttpOnly");
                send(ex, 200, J, "{\"ok\":true}");
            } else send(ex, 401, J, err("Wrong username or password"));
        } else if (!isAdmin(ex)) {
            send(ex, 401, J, err("Admin login required"));
        } else if (p.equals("/api/me")) {
            send(ex, 200, J, "{\"ok\":true}");
        } else if (p.equals("/api/create") && m.equals("POST")) {
            Survey s = new Survey();
            s.title = f.getOrDefault("title", "").trim();
            s.description = f.getOrDefault("description", "").trim();
            for (String line : f.getOrDefault("questions", "").split("\\r?\\n")) {
                String[] a = line.split("\\|");
                if (a.length < 2) continue;
                Question qu = new Question();
                qu.type = a[0].trim().toLowerCase(); qu.text = a[1].trim();
                if (qu.type.equals("choice")) {
                    if (a.length < 3) continue;
                    for (String o : a[2].split(",")) if (!o.trim().isEmpty()) qu.options.add(o.trim());
                } else if (!qu.type.equals("rating") && !qu.type.equals("text")) continue;
                s.questions.add(qu);
            }
            if (s.title.isEmpty() || s.questions.isEmpty()) { send(ex, 400, J, err("Add a title and at least one valid question")); return; }
            s.id = nextId++; surveys.add(s); save();
            send(ex, 200, J, "{\"ok\":true}");
        } else if (p.equals("/api/delete") && m.equals("POST")) {
            surveys.removeIf(s -> String.valueOf(s.id).equals(q.get("id"))); save();
            send(ex, 200, J, "{\"ok\":true}");
        } else if (p.equals("/api/results")) {
            Survey s = find(q.get("id"));
            if (s == null) { send(ex, 404, J, err("Survey not found")); return; }
            send(ex, 200, J, resultsJson(s));
        } else send(ex, 404, J, err("Not found"));
    }

    static String surveyJson(Survey s) {
        String qs = s.questions.stream().map(qu -> "{\"type\":" + js(qu.type) + ",\"text\":" + js(qu.text) + ",\"options\":"
            + qu.options.stream().map(Main::js).collect(Collectors.joining(",", "[", "]")) + "}")
            .collect(Collectors.joining(",", "[", "]"));
        return "{\"id\":" + s.id + ",\"title\":" + js(s.title) + ",\"description\":" + js(s.description)
            + ",\"count\":" + s.responses.size() + ",\"questions\":" + qs + "}";
    }

    static String resultsJson(Survey s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.questions.size(); i++) {
            Question qu = s.questions.get(i);
            List<String> labels = new ArrayList<>(), answers = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            double sum = 0;
            if (qu.type.equals("rating")) for (int k = 1; k <= 5; k++) labels.add("" + k);
            if (qu.type.equals("choice")) labels.addAll(qu.options);
            for (int k = 0; k < labels.size(); k++) counts.add(0);
            for (List<String> r : s.responses) {
                String a = r.get(i);
                if (qu.type.equals("text")) { if (!a.isEmpty()) answers.add(a); continue; }
                int idx = labels.indexOf(a);
                if (idx >= 0) counts.set(idx, counts.get(idx) + 1);
                if (qu.type.equals("rating")) sum += Double.parseDouble(a);
            }
            double avg = s.responses.isEmpty() ? 0 : sum / s.responses.size();
            out.add("{\"text\":" + js(qu.text) + ",\"type\":" + js(qu.type) + ",\"avg\":" + String.format(Locale.US, "%.2f", avg)
                + ",\"labels\":" + labels.stream().map(Main::js).collect(Collectors.joining(",", "[", "]"))
                + ",\"counts\":" + counts + ",\"answers\":" + answers.stream().map(Main::js).collect(Collectors.joining(",", "[", "]")) + "}");
        }
        return "{\"title\":" + js(s.title) + ",\"total\":" + s.responses.size() + ",\"questions\":" + out.stream().collect(Collectors.joining(",", "[", "]")) + "}";
    }

    // ---------- Static files ----------
    static void files(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        Path f = WEB.resolve(p.equals("/") ? "index.html" : p.substring(1)).normalize();
        if (!f.startsWith(WEB) || !Files.isRegularFile(f)) { send(ex, 404, "text/plain", "Not found"); return; }
        String n = f.toString();
        String type = n.endsWith(".html") ? "text/html" : n.endsWith(".css") ? "text/css" : n.endsWith(".js") ? "text/javascript" : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(f));
    }

    // ---------- Helpers ----------
    static Survey find(String id) { return surveys.stream().filter(s -> String.valueOf(s.id).equals(id)).findFirst().orElse(null); }
    static boolean isAdmin(HttpExchange ex) {
        String c = ex.getRequestHeaders().getFirst("Cookie");
        return c != null && c.contains("admin=" + TOKEN);
    }
    static String err(String m) { return "{\"error\":" + js(m) + "}"; }
    static String js(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default: b.append(c);
            }
        }
        return b.append('"').toString();
    }
    static Map<String, String> params(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String kv : s.split("&")) {
            String[] a = kv.split("=", 2);
            m.put(URLDecoder.decode(a[0], StandardCharsets.UTF_8), a.length > 1 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
    static void send(HttpExchange ex, int code, String type, String body) throws IOException { send(ex, code, type, body.getBytes(StandardCharsets.UTF_8)); }
    static void send(HttpExchange ex, int code, String type, byte[] b) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }
    static void save() {
        try (ObjectOutputStream o = new ObjectOutputStream(Files.newOutputStream(DB))) { o.writeObject(surveys); o.writeInt(nextId); }
        catch (IOException e) { System.out.println("Save failed: " + e.getMessage()); }
    }
    @SuppressWarnings("unchecked")
    static void load() {
        if (!Files.exists(DB)) return;
        try (ObjectInputStream i = new ObjectInputStream(Files.newInputStream(DB))) { surveys = (List<Survey>) i.readObject(); nextId = i.readInt(); }
        catch (Exception e) { System.out.println("Starting fresh: " + e.getMessage()); }
    }
}