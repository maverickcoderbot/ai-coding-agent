# Raw run outputs (demo-repo-big RAG demo, qwen3:8b on RTX 2060)

One file per run: `q{N}-{before|after}.txt`. `before` = `before.jar` (no `search_code`),
`after` = `after.jar` (with `search_code`). Questions are in `../RAG-QUESTIONS.md`.

## Provenance / what is lost

The runs were executed in short `exec` sessions whose stdout was being written to
`/tmp/after-q*.log`, but those exec sessions were **reaped before the output flushed**
(the files are 0 bytes), and the `ollama serve`/`ollama` logs only contain request lines,
not the generated answer text. So most raw final answers were **not durably captured**.

Honest status of each file (NOT reconstructed from memory — only verbatim text is marked RAW):

| Run       | Status |
|-----------|--------|
| q1-before | LOST — not captured. Only a paraphrase exists; see file. |
| q1-after  | LOST — not captured. Only a paraphrase exists; see file. |
| q2-before | LOST — not captured. Only a paraphrase exists; see file. |
| q2-after  | LOST — not captured. Only a paraphrase exists; see file. |
| q3-before | RAW — verbatim final answer preserved. |
| q3-after  | RAW — verbatim final answer preserved. |

To recover any LOST run, re-run the jar (needs Ollama up + qwen3:8b) and overwrite the file.
Do not hand-edit or reconstruct answers to look better.
