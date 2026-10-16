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
