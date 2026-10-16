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
   - Answer: `src/main/java/org/springframework/samples/petclinic/owner/Owner.java`
     — the `addPet(...)` method loops `getPets()` and rejects a duplicate name
     (~lines 104–112).
   - Why retrieval can miss: the logic sits inside `Owner.addPet`, a method named for
     *adding*, not for *duplicate* or *name*. Line-based (non-AST) chunking can split
     the dedup loop from the `addPet` signature across a ~40-line window, so the chunk
     that scores highest may be a nearby accessor and the real loop lands outside
     top-5. This is the "what AST chunking would fix" beat.

## Actual results (qwen3:8b on an RTX 2060 6GB, 2026-10-16)

Index (main source only): 66 chunks, ~4s build, 692 KB `.rag/index.json`, ~0.9s query.
Honest, un-tuned. qwen3:8b only partly fits the 2060 (30% CPU / 70% GPU), so runs are slow.

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
- **Q3 (duplicate pet name) — before MISS (hallucinated), after CORRECT.**
  - Before: invents a non-existent `schema.sql` UNIQUE(owner_id, name) DB constraint.
  - After: retrieves `Owner.java:97-136` and correctly answers **Owner.java, `addPet`, ~97-136**.

Takeaway for the video: `search_code` reliably puts the right code in front of the model and
kills the "guess a filename, then hallucinate" failure mode (clearest on Q3). The remaining misses
are honest and instructive — one is retrieval ranking (Q1: the right chunk ranks #9, below top-5), one is the 8B model not using a
good hit (Q2). Both are the "what you'd improve next" beats (Q1: a reranker or larger top-k; Q2: a stronger/larger-context model).
