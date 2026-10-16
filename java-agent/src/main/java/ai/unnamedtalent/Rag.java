package ai.unnamedtalent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Minimal code search: chunk the project, embed each chunk with a local model,
 * and answer a query by cosine similarity. No vector database -- one JSON file
 * and a brute-force scan, which is plenty at this size and easy to explain.
 *
 *   embeddings: Ollama POST /api/embed with nomic-embed-text (ollama pull nomic-embed-text)
 *   index:      .rag/index.json  (one entry per chunk: path, lines, text, vector)
 */
public class Rag {

    static final String EMBED_URL = "http://localhost:11434/api/embed";
    static final String EMBED_MODEL = "nomic-embed-text";
    static final int CHUNK = 40;        // lines per chunk
    static final int OVERLAP = 20;      // lines shared with the previous chunk; a half-chunk
                                        // overlap keeps an annotation and the field/line it guards
                                        // in the same chunk, so the answer isn't split across a
                                        // boundary and ranked out of the top-K.
    static final int TOP_K = 5;

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    // One chunk of a file plus its embedding (null until embedded).
    record Chunk(String path, int start, int end, String text, double[] vector) {}

    // --- 1. CHUNK: split every source file into overlapping line windows ---
    static List<Chunk> chunk(Path root) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        try (Stream<Path> w = Files.walk(root)) {
            List<Path> files = w.filter(Files::isRegularFile)
                    .filter(Rag::isSource)
                    .collect(Collectors.toList());
            for (Path f : files) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i += (CHUNK - OVERLAP)) {
                    int end = Math.min(i + CHUNK, lines.size());
                    String text = String.join("\n", lines.subList(i, end));
                    if (!text.isBlank()) {
                        chunks.add(new Chunk(root.relativize(f).toString(), i + 1, end, text, null));
                    }
                    if (end == lines.size()) break;
                }
            }
        }
        return chunks;
    }

    // Only main source code; skip build output, VCS, the index, and tests
    // (test files restate the question's words and otherwise crowd out the real code).
    static boolean isSource(Path p) {
        String s = p.toString();
        if (s.contains("/target/") || s.contains("/.git/") || s.contains("/.rag/")
                || s.contains("/test/")) return false;
        return s.endsWith(".java");
    }

    // --- 2. EMBED: ask Ollama for a vector per input (batched in one call) ---
    @SuppressWarnings("unchecked")
    static List<double[]> embed(List<String> inputs) throws Exception {
        String body = JSON.writeValueAsString(Map.of("model", EMBED_MODEL, "input", inputs));
        HttpRequest req = HttpRequest.newBuilder(URI.create(EMBED_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res;
        try {
            res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("cannot reach Ollama at " + EMBED_URL
                    + " -- is it running? (ollama pull " + EMBED_MODEL + ")");
        }
        if (res.statusCode() != 200) {
            throw new RuntimeException("embed failed (" + res.statusCode() + "): " + res.body());
        }
        Map<String, Object> parsed = JSON.readValue(res.body(), Map.class);
        List<List<Number>> vecs = (List<List<Number>>) parsed.get("embeddings");
        List<double[]> out = new ArrayList<>();
        for (List<Number> v : vecs) {
            double[] arr = new double[v.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = v.get(i).doubleValue();
            out.add(arr);
        }
        return out;
    }

    // --- 3. INDEX: chunk + embed + write .rag/index.json (built once, reused) ---
    static List<Chunk> buildIndex(Path root) throws Exception {
        List<Chunk> chunks = chunk(root);
        List<Chunk> embedded = new ArrayList<>();
        // batch so one HTTP round-trip embeds many chunks
        for (int i = 0; i < chunks.size(); i += 64) {
            List<Chunk> batch = chunks.subList(i, Math.min(i + 64, chunks.size()));
            List<double[]> vecs = embed(batch.stream().map(Chunk::text).collect(Collectors.toList()));
            for (int j = 0; j < batch.size(); j++) {
                Chunk c = batch.get(j);
                embedded.add(new Chunk(c.path(), c.start(), c.end(), c.text(), vecs.get(j)));
            }
        }
        Path index = root.resolve(".rag/index.json");
        Files.createDirectories(index.getParent());
        JSON.writeValue(index.toFile(), embedded);
        return embedded;
    }

    static List<Chunk> loadOrBuild(Path root) throws Exception {
        Path index = root.resolve(".rag/index.json");
        if (Files.exists(index)) {
            Chunk[] arr = JSON.readValue(index.toFile(), Chunk[].class);
            return List.of(arr);
        }
        return buildIndex(root);
    }

    // --- 4. RETRIEVE: embed the query, cosine against every chunk, return top K ---
    static String search(String query) throws Exception {
        List<Chunk> chunks = loadOrBuild(Path.of("."));
        double[] q = embed(List.of(query)).get(0);
        return chunks.stream()
                .sorted((a, b) -> Double.compare(cosine(q, b.vector()), cosine(q, a.vector())))
                .limit(TOP_K)
                .map(c -> c.path() + ":" + c.start() + "-" + c.end() + "\n" + numbered(c))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    // Prefix each line with its real file line number at render time only; the
    // embedded text and the index format are unchanged, so existing indexes stay valid.
    static String numbered(Chunk c) {
        String[] lines = c.text().split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(c.start() + i).append(": ").append(lines[i]);
        }
        return sb.toString();
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }

    // Build the index from the CLI: `java -cp ... ai.unnamedtalent.Rag --index`
    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        List<Chunk> chunks = buildIndex(Path.of("."));
        System.out.printf("indexed %d chunks in %.1fs%n", chunks.size(), (System.currentTimeMillis() - t0) / 1000.0);
    }
}
