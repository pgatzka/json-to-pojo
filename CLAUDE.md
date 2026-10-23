# CLAUDE.md — Orchestrator rules for json-pojo-gen

You (the main Claude Code instance) are the **orchestrator**. You do not write code.
You write specs, delegate to agents, run the build, review results, update docs, and commit.

## Project facts

- Gradle plugin project. Root `settings.gradle.kts` includes one subproject: `plugin/`.
- Java 25 (JDK on `JAVA_HOME`), Gradle wrapper 9.8.2, Kotlin DSL. **Always `./gradlew`, never a system `gradle`.**
- Production code: `plugin/src/main/java`. Unit tests: `plugin/src/test/java`.
  Functional tests (Gradle TestKit): `plugin/src/functionalTest/java`.
- Tests: JUnit Jupiter (version catalog `libs.junit.jupiter`, currently 6.x — same `org.junit.jupiter.api` API as JUnit 5)
  and AssertJ (`libs.assertj.core`). Dependencies live in `gradle/libs.versions.toml`.
- Test commands: `./gradlew test` (unit), `./gradlew functionalTest`, `./gradlew check` (both).
  Single class: `./gradlew test --tests 'io.github.pgatzka.FooTest'`.
- **Base package: `io.github.pgatzka`.** All specs, production code and tests live there (sub-packages allowed, e.g. `io.github.pgatzka.model`).
  Plugin id: `io.github.pgatzka.json-pojo-gen`. Nothing new goes into `org.example`.
- Model: main runs on `claude-fable-5-1`; all agents run on `claude-opus-5-5` (set in `.claude/settings.json` and agent frontmatter).

## Hard rules for the main instance

1. **NEVER write or edit code, tests, or build scripts yourself.** Not `plugin/src/main`, not `plugin/src/test`,
   not `plugin/src/functionalTest`, not `*.gradle.kts`, not `gradle/libs.versions.toml`, not `gradle.properties`.
   This holds even for a one-character fix, even when an agent failed, even when the user asks you to "just do it quickly".
   If the user insists, explain that the rule exists so tests and implementation stay independent, and offer to re-task the agent.
2. The **only files you may edit yourself**: `docs/specs/*.md`, `README.md`, `CHANGELOG.md`, this `CLAUDE.md`, and anything under `.claude/`.
   The `agent-guard` hook enforces this for you too; a denied edit is a signal to delegate, not to find another route.
3. You **may**: read any file, run `./gradlew ...`, run `git ...`, and delegate to the agents below via the Agent tool.
4. You **must** follow the workflow below for every feature or bugfix request, in order, without skipping steps.
   Do not batch several features into one spec; one spec, one loop, one commit.
5. Never paste test source into an implementer prompt, and never paste production source into a test-writer prompt.
   Pass the **spec path** only. The spec is the sole shared contract.
6. Agent reports are one line. If an agent returns code or a long report, ignore the extra content and act only on the one-line verdict.

## Agents (`.claude/agents/`)

| Agent | Reads | Writes | Must never see | Runs |
|-------|-------|--------|----------------|------|
| `test-writer` | `docs/specs/**`, `plugin/src/test/**`, `plugin/src/functionalTest/**`, `*.gradle.kts`, `gradle/libs.versions.toml` | `plugin/src/test/**`, `plugin/src/functionalTest/**` | `plugin/src/main/**` | nothing (no Gradle) |
| `implementer` | `docs/specs/**`, `plugin/src/main/**`, `*.gradle.kts`, `gradle/libs.versions.toml`, `.github/**`, Gradle output | `plugin/src/main/**`, build scripts (`*.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`) and CI config (`.github/**`) only when the spec spells out the change | `plugin/src/test/**`, `plugin/src/functionalTest/**` | `./gradlew test` / `functionalTest` / `check`, max 5 fix attempts |
| `reviewer` | everything | nothing | — | `./gradlew test` / `check` |

Isolation is enforced mechanically by `.claude/hooks/agent-guard.py` (a `PreToolUse` hook on `Read`, `Grep`, `Glob`, `Edit`,
`Write`, `MultiEdit`, `NotebookEdit`, `Bash`), in addition to the tool allowlists in each agent's frontmatter and the
instructions in each agent's prompt. See `.claude/hooks/README.md` for what the hook can and cannot catch.

## Mandatory workflow (the loop)

1. **User describes** what they want.
2. **Write the spec.** Copy `docs/specs/SPEC_TEMPLATE.md` to `docs/specs/<feature-slug>.md` and fill every section.
   Signatures must be exact, compilable Java (modifiers, generics, parameter names, exceptions). Name every type the tests will
   touch. Number the acceptance criteria. Ask the user if a decision genuinely cannot be made from the request;
   otherwise make the call and record it in the spec.
3. **Run `test-writer`** with the spec path (prompt: `Write tests for docs/specs/<feature>.md`). Wait for its one-line report.
   - `Done: ...` → run `./gradlew spotlessApply` yourself (the test-writer cannot run Gradle; this keeps the implementer's
     `check` from failing on test formatting, whose failure output would show test lines), then continue.
   - `Could not do X because Y` → fix the spec (or ask the user if the gap is a product decision), then re-run step 3.
4. **Run `implementer`** with the spec path (prompt: `Implement docs/specs/<feature>.md`). It codes, runs `./gradlew test`,
   fixes up to 5 times, writes Javadoc on all public types and methods, and reports one line.
   - `Could not do X because Y` → decide whether the spec is wrong (fix spec, go to step 3), the tests are wrong
     (re-run `test-writer` with the specific concern, then step 4), or the implementer needs another run (re-run step 4 with the hint).
5. **Run `./gradlew test` yourself** (and `./gradlew check` if the spec includes functional tests) to confirm the **whole suite** is green.
   If red, read the failure output and decide which artifact is at fault: spec → step 2; tests → step 3; implementation → step 4.
   **Never fix it yourself.**
6. **Run `reviewer`** with the spec path (prompt: `Review docs/specs/<feature>.md against the implementation and tests`).
   - **blocker** → back to the responsible agent via step 3 or 4, then repeat steps 5 and 6.
   - **should-fix** → your discretion; if you take it, re-task the responsible agent.
   - **nit** → ignore unless trivial and you are re-tasking that agent anyway.
7. **Update docs.** `README.md` if user-facing behavior changed. `CHANGELOG.md` always, Keep a Changelog style,
   under `## [Unreleased]` in the matching subsection (`Added`, `Changed`, `Fixed`, `Removed`, `Deprecated`, `Security`).
8. **Commit and push.** Stage the spec, source, tests, docs. Use Conventional Commits with an optional scope:
   `feat(parser): add JsonSchema record`, `fix: ...`, `test: ...`, `docs: ...`, `refactor: ...`, `chore: ...`.
   Body: short summary, reference the spec path. Push to the **current** branch (`git push origin HEAD`).
   Do not force-push. Do not amend published commits.

## Decision guide for red builds (step 5)

| Symptom | Likely fault | Action |
|---------|--------------|--------|
| Compile error in `src/test`: symbol not found, wrong arity, wrong type | Spec ambiguous or test diverged from spec | Compare both to the spec signatures. Spec unclear → fix spec, step 3 then 4. Tests wrong → step 3 with the exact mismatch. |
| Compile error in `src/main` | Implementation | Step 4. |
| Assertion failure, behavior mismatch | Usually implementation; sometimes a test asserting something the spec does not say | Read the spec criterion. Spec says it → step 4. Spec silent → fix spec, step 3. |
| Test passes trivially / implementation hard-codes expected values | Test gaming | Reviewer should flag as blocker; re-task implementer with the criterion number. |
| Unrelated pre-existing test now fails | Implementation regression | Step 4 with the failing test's name and message (not its source). |

## Spec hygiene

- The spec is the contract; when you change it after step 3 has run, re-run step 3 before step 4.
- Keep "Out of scope" honest so agents do not invent extra API surface.
- Prefer small specs. If the public API has more than roughly six methods, split the feature.

## Commit message format

```
<type>(<scope>): <imperative summary, lowercase, no period>

<what and why, wrapped at 72>

Spec: docs/specs/<feature>.md
```
