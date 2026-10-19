# Code-search test: results

Companion to [QUESTIONS.md](QUESTIONS.md). Three fixes for AI code search, tested on 37 searches (18 questions) across three codebases. Embedding model: nomic-embed-text, cosine similarity, top five handed to the model.

**How to read the numbers.** *Top-five hits* = searches where a chunk holding the answer lines is in the top five (out of 17 PetClinic, 10 Gson, 10 Spark, 37 in total). *Average rank score* = mean of 1 / rank of the best answer chunk (1.0 = always first). *Current search* = the code from the previous video: 40-line chunks, 20 lines of overlap, search by meaning, top five.

## 1. Bigger chunks (80 lines instead of 40, same overlap)
| codebase | current search | 80-line chunks |
|---|---|---|
| PetClinic (17) | 15 | 10 (worse) |
| Gson (10) | 7 | 8 (better) |
| Spark (10) | 9 | 8 (worse; 60-line chunks scored 10) |

Chunk size depends on the code. On ten searches a one-search difference is a clue, not a rule.

## 2. Cutting code at method boundaries (a real Java parser, one declaration per chunk)
Tiny neighbours are merged up to 40 lines and declarations over 80 lines are split into 40-line windows with 20 overlap. Files with no members of their own are left as plain windows.

| codebase | current search (hits / avg rank score) | by method |
|---|---|---|
| PetClinic (17) | 15 / 0.651 | 11 / 0.466 |
| Gson (10) | 7 / 0.395 | 8 / 0.585 |
| Spark (10) | 9 / 0.748 | 8 / 0.455 |
| all 37 | 31 / 0.608 | 27 / 0.495 |

Verdict: worse overall. The rules were written down before the run.

## 3. Reranking the top 20
Take the top 20 by meaning, score them again, keep the best five. The second pass only reorders those 20; it cannot add candidates.

- **Keyword rerank:** `cosine + 0.15 x (fraction of the question's words found in the chunk)`, short and common words ignored. About 30 lines of plain Java, no model, no tuning for this test.
- **Cross-encoder rerank:** `ms-marco-MiniLM-L-6-v2` (91 MB), run on the CPU: about 1.7 seconds per search plus about 10 seconds to load once.

Top-five hits / average rank score, on 40/20 chunks:

| codebase | no rerank | keyword rerank | cross-encoder |
|---|---|---|---|
| PetClinic (17) | 15 / 0.651 | 16 / 0.866 | 15 / 0.834 |
| Gson (10) | 7 / 0.392 | 8 / 0.558 | 8 / 0.478 |
| Spark (10) | 9 / 0.748 | 9 / 0.864 | 10 / 0.767 |
| all 37 | 31 / 0.607 | 33 / 0.783 | 33 / 0.720 |

On method-boundary chunks (all 37): no rerank 27 / 0.494, keyword 31 / 0.614, cross-encoder 30 / 0.725.

(Per-repo no-rerank scores differ in the third decimal from section 2 for Gson and Spark because a top-20 reranker cannot move an answer ranked below 20, so those searches count as a miss here.)

The keyword rerank did not fix the video's opening example (the main duplicate-pet-name check ranks 6th without the rerank and 7th with it); the cross-encoder moved it to 1st, at the cost of a few word-match questions.

## Limits, in plain words
- 37 searches from 18 questions on three codebases is a small test: leads, not laws.
- The keyword formula and its 0.15 weight come from an earlier experiment on PetClinic, so PetClinic cannot fairly judge them. Gson and Spark never touched them.
- The same 37 searches chose the setup shown, so they are no longer an untouched test.
- PetClinic has 17 searches because its questions already existed from the previous video; Gson and Spark have 10 each.

## 4. Live runs (qwen3:8b, the three PetClinic questions, three runs each, two setups)
Headless runs on the same day with a 600-second cap, scored against the source. The scoring categories were defined after the answers were seen.

| question | without rerank | with keyword rerank |
|---|---|---|
| owner phone number | 2 exact, 1 off by a line | 3 exact |
| pet name and type | 0 exact, 2 close, 1 wrong | 3 exact |
| duplicate pet names | 1 exact, 2 found only the database error handler | 1 exact, 2 ran out of time with no answer |

Retrieval was reproduced after the run from each run's own search words. The right chunk was ranked first on question 1 in both setups, and the model's own wording changed from run to run, so the exact-versus-close differences there are model variation. Nine runs per setup cannot separate the effect of the rerank from that variation: **inconclusive**. Why two question-3 runs ran out of time is not known; the server log was not saved.
