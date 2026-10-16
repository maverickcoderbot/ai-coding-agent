# demo-repo-big — `search_code` demo questions

Demo codebase: **Spring PetClinic** (Apache-2.0, `spring-projects/spring-petclinic`), ~30 main Java
files. It's a good target for `search_code` because the filenames don't give the answers away — an
agent that only has `list_files` plus guessing opens the wrong files first. Validation lives in
entity annotations and in methods named for something else.

Run (Ollama up, both models pulled — see the top-level README):

```bash
cd demo-repo-big
java -jar ../java-agent/target/coding-agent.jar "<question>"
# first call embeds the repo into .rag/index.json, then answers
```

## The three known-answer questions

1. **"Where do we validate that an owner's telephone number is exactly 10 digits?"**
   - Answer: `src/main/java/org/springframework/samples/petclinic/owner/Owner.java` — the
     `telephone` field carries `@Pattern(regexp = "\\d{10}", ...)` (around line 64).
   - Why it's a good test: the rule is a bean-validation annotation on an entity field, not in any
     `*Validator` class. Guessing "PetValidator / OwnerController" by filename misses it.

2. **"Where is a pet's name and type checked as required?"**
   - Answer: `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java` —
     `errors.rejectValue("name", REQUIRED, REQUIRED)` for the name and the matching check for the type.
   - Why: this is the control. The filename does match the question, so retrieval and plain guessing
     should both land here.

3. **"How does the app stop you adding two pets with the same name to one owner?"**
   - Answer: the duplicate-**name** check lives in
     `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`:
     - `processCreationForm` (around line 111): rejects the field when
       `owner.getPet(pet.getName(), true) != null` with `rejectValue("name", "duplicate", ...)`.
     - `processUpdateForm` (around lines 150–154): looks up `owner.getPet(petName, false)` and rejects
       the name when an existing pet with a different id already has it.
     - both delegate to `Owner.getPet(String name, boolean ignoreNew)` (around lines 147–158,
       `equalsIgnoreCase`).
   - A second, valid layer: a **DB UNIQUE constraint** — `UNIQUE (owner_id, name)` in
     `db/h2/schema.sql` and `db/mysql/schema.sql`, and a unique index on `(owner_id, LOWER(name))` in
     `db/postgres/schema.sql`. An answer that cites the DB constraint is also correct — it really does
     prevent duplicates, just at the persistence layer rather than the controller.
   - Note: `Owner.addPet(...)` does **not** check names — it only skips `null`, the identical object,
     and a matching non-new id. The filename `Owner`/`addPet` suggests the dedup lives there, but it
     doesn't; the real check is in a controller, split across two form handlers, delegating to a
     method (`getPet`) named for lookup, not for duplicates.

These three are deliberately non-obvious so that semantic retrieval has something to prove over a
filename guess: answer 1 is an annotation on a field, answer 3 is split across two handlers in a file
you wouldn't guess from the question.
