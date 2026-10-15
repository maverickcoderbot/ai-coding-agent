package ai.unnamedtalent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The SAME agent loop as {@link LocalAgent}, but using Anthropic's own SDK to run
 * on Claude (more capable, paid per token). Reuses LocalAgent.SYSTEM and
 * LocalAgent.runTool -- only the client, model, and message types differ.
 *
 *   export ANTHROPIC_API_KEY=sk-ant-...
 *   cd ../demo-repo-java
 *   java -cp ../java-agent/target/coding-agent.jar ai.unnamedtalent.Agent "the task"
 */
public class Agent {

    static final AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    static final String MODEL = "claude-opus-5"; // or "claude-sonnet-5" for cheaper/faster

    // Same four tools, expressed in Anthropic's tool shape.
    static Tool tool(String name, String desc, Map<String, String> props, List<String> required) {
        Tool.InputSchema.Properties.Builder pb = Tool.InputSchema.Properties.builder();
        props.forEach((k, v) -> pb.putAdditionalProperty(k, JsonValue.from(Map.of("type", v))));
        return Tool.builder()
                .name(name)
                .description(desc)
                .inputSchema(Tool.InputSchema.builder().properties(pb.build()).required(required).build())
                .build();
    }

    static final List<Tool> TOOLS = List.of(
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

    @SuppressWarnings("unchecked")
    static void agent(String task, int maxIters) throws Exception {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(task).build());

        for (int n = 0; n < maxIters; n++) {
            MessageCreateParams.Builder pb = MessageCreateParams.builder()
                    .model(MODEL).maxTokens(16000L).system(LocalAgent.SYSTEM).messages(messages);
            TOOLS.forEach(pb::addTool);
            Message resp = client.messages().create(pb.build());

            messages.add(resp.toParam());

            List<ToolUseBlock> calls = resp.content().stream()
                    .flatMap(b -> b.toolUse().stream()).toList();

            if (calls.isEmpty()) {
                resp.content().stream().flatMap(b -> b.text().stream())
                        .forEach(t -> System.out.println(t.text()));
                return;
            }

            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock c : calls) {
                Map<String, Object> input = (Map<String, Object>) c._input().convert(Map.class);
                System.out.println("  -> " + c.name() + " " + input);
                String out = LocalAgent.runTool(c.name(), input);
                results.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder().toolUseId(c.id()).content(out).build()));
            }
            messages.add(MessageParam.builder().role(MessageParam.Role.USER)
                    .contentOfBlockParams(results).build());
        }
        System.out.println("Stopped: hit max iterations.");
    }

    public static void main(String[] args) throws Exception {
        agent(args[0], 25);
    }
}
