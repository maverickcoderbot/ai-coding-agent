# demo-repo-big — RAG demo questions

Demo codebase: **Spring PetClinic** (Apache-2.0, `spring-projects/spring-petclinic`),
30 main Java files / 50 with tests. Chosen because the filenames don't give the
answers away — an agent that only has `list_files` + guessing has to open the wrong
files first. Non-obvious on purpose: validation lives in entity annotations and in a
method named for something else.

Run (on the GPU box, Ollama up):
```
cd demo-repo-big
# before: no search tool — the agent guesses paths
java -jar ../java-agent/target/coding-agent.jar "<question>"
# the agent now has search_code; it embeds the repo on first call (.rag/index.json)
```

## The 3 known-answer questions

1. **"Where do we validate that an owner's telephone number is 10 digits?"**
   - Answer: `src/main/java/org/springframework/samples/petclinic/owner/Owner.java`
     — the `telephone` field carries `@Pattern(regexp = "\\d{10}", ...)` (~line 64).
   - Why it's a good test: the rule is a bean-validation annotation on an entity
     field, not in any `*Validator` class. Guessing "PetValidator / OwnerController"
     by filename misses it.

2. **"Where is a pet's name and type checked as required?"**
   - Answer: `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java`
     — `errors.rejectValue("name", REQUIRED, REQUIRED)` etc.
   - Why: the easy one. The filename DOES match the question, so it's the control —
     retrieval and plain guessing should both land here. Good contrast on camera.

3. **"How does the app stop you adding two pets with the same name to one owner?"**  ← honest-miss candidate
   - Answer (corrected 2026-10-16): the duplicate-**name** check lives in
     `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`:
     - `processCreationForm` (~line 111): `if (StringUtils.hasText(pet.getName()) && pet.isNew()
       && owner.getPet(pet.getName(), true) != null) result.rejectValue("name", "duplicate", "already exists");`
     - `processUpdateForm` (~lines 150–154): `Pet existingPet = owner.getPet(petName, false);
       if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId()))
       result.rejectValue("name", "duplicate", "already exists");`
     - both use `Owner.getPet(String name, boolean ignoreNew)` (Owner.java ~147–158, `equalsIgnoreCase`).
   - Second, valid layer: a **DB UNIQUE constraint** — `db/h2/schema.sql:55` and `db/mysql/schema.sql:47`
     `UNIQUE (owner_id, name)`, `db/postgres/schema.sql:45` a unique index on `(owner_id, LOWER(name))`.
     An answer that cites the DB constraint is correct (it really does prevent duplicates), just at the
     persistence layer rather than the controller.
   - NOTE: `Owner.addPet(...)` (Owner.java ~100–112) does **not** check names — it only skips `null`,
     the identical object (`getPets().contains(pet)`), and a matching non-new id. The earlier version of
     this file claimed `addPet` did the dedup; that was wrong.
   - Why it's a good test: the filename `Owner`/`addPet` suggests the dedup lives there, but it doesn't;
     the real check is in a controller, split across two form handlers, delegating to a method
     (`getPet`) named for lookup, not for duplicates.

## Actual results (qwen3:8b on an RTX 2060 6GB, 2026-10-16)

Index (main source only): 66 chunks, ~4s build, 692 KB `.rag/index.json`, ~0.9s query.
Honest, un-tuned. qwen3:8b only partly fits the 2060 (30% CPU / 70% GPU), so runs are slow.

Raw final answers are in `runs/` (one file per run). **Only `q3-before` and `q3-after` were
durably captured verbatim; the other four raw outputs were lost** when the exec sessions were
reaped (0-byte logs) — their `runs/` files are labelled LOST with a paraphrase, not reconstructed
as if they were the model's words.

### Per-run score vs real source (ground truth verified by reading the files)

Ground truth: Q1 = `Owner.java:64` `@Pattern(regexp="\\d{10}")`. Q2 = `PetValidator.java:44` (name
required) + `51-52` (type required). Q3 = `PetController.java` create ~111-112 / edit ~150-154 via
`Owner.getPet(name)` (`Owner.java` ~147-158) **AND** a DB `UNIQUE(owner_id,name)` constraint
(`db/h2/schema.sql:55`, `db/mysql/schema.sql:47`, `db/postgres/schema.sql:45` on `LOWER(name)`).

| Run       | Score   | One-line reason |
|-----------|---------|-----------------|
| Q1 before | Wrong   | Said "not explicitly implemented," invented `OwnerValidator`/`@Size`; never found `Owner.java:64`. |
| Q1 after  | Wrong   | Retrieval returned `Owner.java:65-104` as #1 (no line 64); right chunk `33-72` ranked #9, below top-5; answer wrong. |
| Q2 before | Wrong   | Hallucinated `@Column(nullable=false)`/`@NotNull` in `Pet.java`; real check is `PetValidator`. |
| Q2 after  | Wrong   | Correct `PetValidator` chunk WAS in top-5, but the model didn't cite it and speculated about `Pet.java`. |
| Q3 before | Partial | DB `UNIQUE(owner_id,name)` is a real, valid persistence-layer answer — but misses the `PetController` app-layer check. |
| Q3 after  | Wrong   | Answered `Owner.addPet` (97-136), which does NOT dedup names; the real check is in `PetController`. |

**Tally: before 0 correct (1 partial), after 0 correct.** Retrieval improved (right file surfaced on
Q2 and Q3, better rank on Q1), but qwen3:8b produced no correct final answer on any question.
For Q3 specifically, **the AFTER retrieval DID surface `PetController.java` in its top-5** (chunks
ranked #1/#2/#3) — the model just chose the #4 `Owner.addPet` chunk instead.

- **Q1 (telephone) — MISS, before and after.**
  - Before (no search): reads PetValidator/OwnerController by guess, never opens Owner.java,
    concludes "not explicitly implemented" and invents an `OwnerValidator`/`@Size`.
  - After (search_code): the TOP hit is `Owner.java:65-104` (cosine 0.578), which starts at line 65
    and does NOT contain the annotation. The chunk that DOES contain `@Pattern(\d{10})` is
    `Owner.java:33-72` (overlap means line 64 is inside it) — but it ranks **#9** (cosine 0.532),
    below the top-5 cutoff, so `search_code` never returns it. qwen3:8b answers wrong.
    So this is a **ranking miss, not a boundary miss**: the right chunk exists and is correctly
    chunked, it's just out-scored by five others (including its own neighbor 65-104). The fix is
    better ranking / larger top-k / a reranker — NOT bigger overlap.
- **Q2 (pet name/type required) — before MISS, after retrieves the answer but model still fumbles.**
  - Before: reads Pet.java, hallucinates `@Column(nullable=false)`/`@NotNull` in files it never opened.
  - After: `PetValidator.java:33-69` (the `rejectValue("name"/"type", REQUIRED)`) is in the top-5,
    but qwen3:8b doesn't cite it and speculates about `Pet.java`. Retrieval right, small model weak.
- **Q3 (duplicate pet name) — re-scored 2026-10-16. Retrieval is RIGHT; qwen3:8b picks the wrong chunk.**
  - Before (no search): answered the **DB UNIQUE constraint** (`schema.sql`, `UNIQUE (owner_id, name)`).
    That is a **real, valid** answer (the constraint exists in all three schema files) — NOT a hallucination.
    It just misses the controller-level check, so call it partially correct (one of two layers).
  - After (search_code): embed-only retrieval for the Q3 query returns a GOOD top-5 — the three
    `PetController.java` chunks that contain the duplicate-name check rank **#1 (161-200, 0.660),
    #2 (193-207, 0.626), #3 (129-168, 0.623)**; the `Owner.getPet(name)` chunk (`Owner.java:129-168`,
    covers 147-158) ranks **#8**. So the right code IS in front of the model. But `Owner.addPet`'s
    chunks (`Owner.java:97-136` #4, `65-104` #5) are also retrieved, and qwen3:8b latched onto
    `addPet` and answered **"Owner.java, addPet, ~97-136"** — which is **WRONG** (addPet does no name
    dedup). So this is a **model-reasoning miss, not a retrieval miss**: good retrieval, wrong chunk chosen.
  - Honest tally change: this is NOT a clean "after = correct" win. After retrieves the right chunks
    but the 8B model answers wrong. See the headline note below.

Honest headline (corrected 2026-10-16): on all 3 questions, `search_code` retrieval is solid —
it puts the right chunks in the top-5 for Q2 and Q3, and ranks the right-but-not-top chunk at #9 for Q1.
But qwen3:8b, the 8B local model, does **not convert that into a correct final answer on any of the three**:
- Q1: the right chunk is retrieved but at #9 (below the top-5 search_code returns) → wrong.
- Q2: the right chunk IS in top-5, but the model doesn't cite it and speculates → wrong.
- Q3: the right PetController chunks are #1/#2/#3, but the model picks the #4 `Owner.addPet` chunk
  and misreads it → wrong.
So the honest before/after is: before = 0/3 (pure guessing, though the Q3 "DB constraint" guess happened
to be a real valid layer); after = retrieval fixes the "which file" problem on 2 of 3 and improves Q1's
rank, but the 8B model's reasoning is the bottleneck, so the **final-answer score stays ~0/3 correct with
this model**. The win to show on camera is RETRIEVAL quality (right code surfaced), not final accuracy —
and the clear "what you'd improve next" is a stronger model (and for Q1, a reranker / larger top-k).
This is the honest version; do not sell it as "1/3 → correct." If we want a correct-answer demo, we need
a bigger model than qwen3:8b.
