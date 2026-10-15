# AI Coding Agent — from scratch (companion code)

A ~140-line AI coding assistant: **an LLM + a few tools + a loop.** It runs on **any LLM you can
reach** — the default is a **free local model (Ollama + Qwen3)**, no API key, nothing leaves your
machine. Swap in Claude, Gemini, OpenAI/Codex, etc. with a few lines.

The whole idea: *an agent is an LLM in a loop with tools.* Four parts — the model, a few tools
(`list_files`, `read_file`, `edit_file`, `run_bash`), the loop (send the conversation → run the tools
the model asks for → feed the results back → repeat until it stops), and a short system prompt.

---

## What's in here
- `java-agent/`
  - `LocalAgent.java` — **the default agent.** Talks to any OpenAI-compatible endpoint; preconfigured
    for a local Qwen3 via Ollama. This is the `java -jar` main class.
  - `Agent.java` — the *same loop* on **Claude** via Anthropic's SDK (the "want more power" swap).
- `demo-repo-java/` — a tiny project with a **failing** test for the agent to fix.

---

## Prerequisites
You need **JDK 21** and **Maven**. Any OS works (macOS, Linux, Windows).
```bash
# macOS (Homebrew)
brew install openjdk@21 maven
# Debian/Ubuntu
sudo apt install openjdk-21-jdk maven
# or download JDK 21 + Maven manually for Windows
java -version   # 21
mvn -version
```
**And an LLM for the agent to call.** Pick one:
- **Free + local (default):** install Ollama (https://ollama.com/download), then `ollama pull qwen3:8b`
  (~5 GB; `qwen3:4b` is lighter/faster). Most modern laptops with 16 GB+ RAM run the 8B model
  comfortably; use `qwen3:4b` on lighter machines.
- **A cloud provider you already use** — Gemini, OpenAI/Codex, Claude, etc. (see "Swap the model").

## Build (once)
```bash
cd java-agent
mvn -q package      # -> target/coding-agent.jar  (default main class = LocalAgent)
```

## Run (default = free + local)
```bash
ollama pull qwen3:8b        # one time; make sure Ollama is running
cd ../demo-repo-java
java -jar ../java-agent/target/coding-agent.jar \
  "add input validation to the signup endpoint and make the tests pass"
```
No API key. Nothing leaves your machine. **Tradeoff:** a local model is slower and less reliable at
tool-calling than a frontier model — it may need more tries or occasionally fumble a call.

## Swap the model (one place)
Edit the three constants at the top of `LocalAgent.java` and rebuild:
```java
static final String BASE_URL = "http://localhost:11434/v1";  // Ollama (default)
static final String API_KEY  = "ollama";
static final String MODEL    = "qwen3:8b";
```
| Provider | BASE_URL | MODEL |
|---|---|---|
| Ollama (local) | `http://localhost:11434/v1` | `qwen3:8b` |
| OpenAI / Codex | `https://api.openai.com/v1` | `gpt-5` |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.5-pro` |

**Claude** uses a different API format, so it has its own file. Point it at Claude with an API key:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd ../demo-repo-java
java -cp ../java-agent/target/coding-agent.jar ai.unnamedtalent.Agent \
  "add input validation to the signup endpoint and make the tests pass"
```

---

## Reset the demo between runs
`demo-repo-java/src/main/java/demo/Signup.java` ships with **no validation** (2 of 3 tests fail). The
agent edits it so all 3 pass. To reset, restore the original two-line method:
```java
public static Map<String, String> signup(String email, String password) {
    return Map.of("email", email, "password", password);
}
```
Check state anytime: `cd demo-repo-java && mvn -q test`.

## ⚠️ Safety note
`run_bash` runs *any* shell command the model requests — fine for this toy repo, but a real assistant
should gate destructive actions (delete, push, deploy) behind human approval. The model only ever
*asks*; your code decides what runs.
