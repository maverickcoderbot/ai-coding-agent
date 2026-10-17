# AI Coding Agent — from scratch (companion code)

A ~140-line AI coding assistant: **an LLM + a few tools + a loop.** It runs on **any LLM you can
reach** — the default is a **free local model (Ollama + Qwen3)**, no API key, nothing leaves your
machine. Swap in Claude, Gemini, OpenAI/Codex, etc. with a few lines.

The whole idea: *an agent is an LLM in a loop with tools.* Four parts — the model, a few tools
(`list_files`, `read_file`, `edit_file`, `run_bash`, and `search_code`), the loop (send the
conversation → run the tools the model asks for → feed the results back → repeat until it stops),
and a short system prompt.

---

## What's in here
- `java-agent/`
  - `LocalAgent.java` — **the default agent.** Talks to any OpenAI-compatible endpoint; preconfigured
    for a local Qwen3 via Ollama. This is the `java -jar` main class.
  - `Agent.java` — the *same loop* on **Claude** via Anthropic's SDK (the "want more power" swap).
  - `Rag.java` — the `search_code` tool: local-embedding code search, no vector DB (see below).
- `demo-repo-java/` — a tiny project with a **failing** test for the agent to fix.
- `demo-repo-big/` — a larger real-world codebase (Spring PetClinic, Apache-2.0) with a few
  known-answer questions, used to exercise `search_code` on a repo big enough that guessing
  filenames doesn't work.

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

## `search_code` — find code by meaning, not by filename

On a repo of any real size, the agent shouldn't guess which file to open. `search_code` gives it a
semantic index of the codebase and returns the most relevant snippets for a plain-English question.

**How it works (`Rag.java`, no vector database):**
1. Split every source file into overlapping line windows (`CHUNK = 40` lines, `OVERLAP = 20`).
2. Embed each window locally with Ollama's `nomic-embed-text` model and cache the vectors in
   `.rag/index.json` at the repo root (built once on the first call; delete the file to rebuild).
3. On a query, embed the question the same way, rank all chunks by cosine similarity, and return the
   top `TOP_K = 5` as `path:start-end` blocks with each line prefixed by its real file line number.

The model is told to call `search_code` **before** guessing paths, and to phrase the query as a full
natural-language sentence (e.g. *"where is the owner telephone number checked to be 10 digits"*)
rather than keywords — sentences embed closer to the surrounding code than bare terms do.

**One extra prerequisite** (the chat model from above still applies):
```bash
ollama pull nomic-embed-text   # the embedding model search_code uses
```

**Try it on `demo-repo-big`:**
```bash
cd demo-repo-big
java -jar ../java-agent/target/coding-agent.jar \
  "Where in the code do we validate that the owner telephone number is exactly 10 digits?"
```
`demo-repo-big/RAG-QUESTIONS.md` lists three known-answer questions whose answers live in
non-obvious places (a bean-validation annotation on an entity field; a check split across two form
handlers), so a filename guess lands on the wrong file first and `search_code` earns its keep.

**Honest limitations:**
- **Small models are the bottleneck.** `search_code` can put the right chunk in the top 5 and a small
  local model (e.g. an 8B) may still read it wrong or cite the wrong neighbour. A bigger/stronger
  model converts good retrieval into correct answers far more reliably.
- **Query wording matters.** Because ranking is pure embedding similarity, the phrasing of the
  question shifts which chunk ranks #1. A sentence that names the concept you want ("duplicate pet
  names per owner") retrieves better than a vague one.
- **Chunk size and overlap are tuning knobs, not constants.** On the demo repo, raising the overlap
  from 8 to 20 changed where the chunks start, and the chunk holding the answer then ranked first.
  On two other Java repos, overlap 20 helped or tied, and the best chunk size varied with file size
  (80-line chunks were worse on small files). A larger overlap also means more chunks and more
  duplicated text. `CHUNK = 40 / OVERLAP = 20` is a default that worked on the demo, not a tuned
  optimum: measure it on your own code, with questions whose answers you already know.
- Ranking is brute-force cosine over every chunk (fine for a repo this size, not for a monorepo), and
  there's no reranker — the first stage's top-K is what the model sees.

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
