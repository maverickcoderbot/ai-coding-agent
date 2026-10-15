package ai.unnamedtalent;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * The smallest possible "hello world" for the local LLM: point the OpenAI-compatible
 * client at a local Qwen3 via Ollama, send ONE message, print the reply. No tools,
 * no loop -- just proof the model is reachable before you run the real agent.
 *
 *   ollama pull qwen3:8b    # make sure Ollama is running
 *   mvn -q package
 *   java -cp target/coding-agent.jar ai.unnamedtalent.Hello
 */
public class Hello {

    static final String BASE_URL = "http://localhost:11434/v1";  // Ollama (default)
    static final String API_KEY = "ollama";      // required by the client; Ollama ignores it
    static final String MODEL = "qwen3:8b";

    public static void main(String[] args) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(BASE_URL).apiKey(API_KEY).build();

        String prompt = args.length > 0 ? args[0] : "Say hello in one short sentence.";

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addUserMessage(prompt)
                .build();

        client.chat().completions().create(params)
                .choices().get(0).message().content()
                .ifPresent(System.out::println);
    }
}
