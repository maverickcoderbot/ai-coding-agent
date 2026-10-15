package ai.unnamedtalent;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An AI coding agent that runs on ANY LLM you can reach over an OpenAI-compatible
 * API. The default below is a FREE local model via Ollama (no API key, nothing
 * leaves your machine). To use a cloud provider instead, change BASE_URL + API_KEY
 * + MODEL (see the table below). For Claude specifically, see Agent.java.
 *
 *   Local (default):  ollama pull qwen3:8b
 *     BASE_URL = http://localhost:11434/v1   API_KEY = "ollama"   MODEL = "qwen3:8b"
 *   OpenAI / Codex:   BASE_URL = https://api.openai.com/v1                 MODEL = "gpt-5" (your key)
 *   Google Gemini:    BASE_URL = https://generativelanguage.googleapis.com/v1beta/openai   MODEL = "gemini-2.5-pro"
 *
 *   build:  mvn -q package
 *   run:    cd ../demo-repo-java && java -jar ../java-agent/target/coding-agent.jar "the task"
 */
public class LocalAgent {

    // ---- Pick your LLM here. Default = free local model via Ollama. ----
    static final String BASE_URL = "http://localhost:11434/v1";
    static final String API_KEY = "ollama";      // required by the client; Ollama ignores it
    static final String MODEL = "qwen3:8b";

    static final OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(BASE_URL).apiKey(API_KEY).build();

    static final String SYSTEM = """
        You are a coding assistant working in the current project directory. \
        Use the tools to list, read, and edit files and run commands to complete \
        the user's task. Make the smallest change that works. When the task is \
        done and tests pass, stop and say what you did.""";

    // --- 1. THE TOOLS: what the model is allowed to ask us to do ---
    static ChatCompletionFunctionTool tool(String name, String desc,
                                           Map<String, String> props, List<String> required) {
        Map<String, Object> properties = props.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Map.of("type", e.getValue())));
        return ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(name)
                        .description(desc)
                        .parameters(FunctionParameters.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(properties))
                                .putAdditionalProperty("required", JsonValue.from(required))
                                .build())
                        .build())
                .build();
    }

    static final List<ChatCompletionFunctionTool> TOOLS = List.of(
            tool("list_files", "List all files under a directory (recursive).",
                    Map.of("dir", "string"), List.of("dir")),
            tool("read_file", "Read and return the full contents of a file.",
                    Map.of("path", "string"), List.of("path")),
            tool("edit_file", "Replace an exact substring in a file. `old` must match exactly once.",
                    Map.of("path", "string", "old", "string", "new", "string"),
                    List.of("path", "old", "new")),
            tool("run_bash", "Run a shell command (e.g. the tests) and return its output.",
                    Map.of("cmd", "string"), List.of("cmd"))
    );

    // --- 2. EXECUTING a tool the model asked for (shared by Agent too) ---
    static String runTool(String name, Map<String, Object> i) throws IOException, InterruptedException {
        switch (name) {
            case "list_files" -> {
                try (Stream<Path> w = Files.walk(Path.of((String) i.get("dir")))) {
                    return w.filter(Files::isRegularFile).map(Path::toString).collect(Collectors.joining("\n"));
                }
            }
            case "read_file" -> {
                return Files.readString(Path.of((String) i.get("path")));
            }
            case "edit_file" -> {
                Path p = Path.of((String) i.get("path"));
                String text = Files.readString(p);
                String oldS = (String) i.get("old");
                String newS = (String) i.get("new");
                int count = text.split(Pattern.quote(oldS), -1).length - 1;
                if (count != 1) {
                    return "ERROR: `old` must match exactly once in " + p + " (found " + count + ").";
                }
                Files.writeString(p, text.replace(oldS, newS));
                return "ok: file edited";
            }
            case "run_bash" -> {
                Process pr = new ProcessBuilder("bash", "-c", (String) i.get("cmd"))
                        .redirectErrorStream(true).start();
                String out = new String(pr.getInputStream().readAllBytes());
                pr.waitFor();
                return out.isBlank() ? "(no output)" : out.substring(0, Math.min(out.length(), 5000));
            }
            default -> {
                return "ERROR: unknown tool " + name;
            }
        }
    }

    // --- 3. THE LOOP: this is the 'agent' ---
    @SuppressWarnings("unchecked")
    static void agent(String task, int maxIters) throws Exception {
        List<ChatCompletionMessageParam> messages = new ArrayList<>();
        messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(SYSTEM).build()));
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(task).build()));

        for (int n = 0; n < maxIters; n++) {
            ChatCompletionCreateParams.Builder pb = ChatCompletionCreateParams.builder()
                    .model(MODEL).messages(messages);
            TOOLS.forEach(pb::addTool);
            ChatCompletionMessage msg = client.chat().completions().create(pb.build())
                    .choices().get(0).message();

            messages.add(ChatCompletionMessageParam.ofAssistant(msg.toParam()));

            Optional<List<ChatCompletionMessageToolCall>> calls = msg.toolCalls();
            if (calls.isEmpty() || calls.get().isEmpty()) { // no tool calls -> it's done
                msg.content().ifPresent(System.out::println);
                return;
            }
            for (ChatCompletionMessageToolCall call : calls.get()) {
                ChatCompletionMessageFunctionToolCall fc = call.asFunction();
                Map<String, Object> input = fc.function().arguments(Map.class);
                System.out.println("  -> " + fc.function().name() + " " + input);
                String out = runTool(fc.function().name(), input);
                messages.add(ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(fc.id()).content(out).build()));
            }
        }
        System.out.println("Stopped: hit max iterations.");
    }

    public static void main(String[] args) throws Exception {
        agent(args[0], 25);
    }
}
