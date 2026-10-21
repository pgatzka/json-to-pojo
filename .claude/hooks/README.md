# agent-guard: how isolation is enforced

`agent-guard.py` is a `PreToolUse` hook registered in `.claude/settings.json` with matcher `*`.
Claude Code runs it before **every** tool call made by the main instance or by any subagent, and pipes
a JSON object on stdin. Inside a subagent that object carries `agent_type` (the agent's `name`),
so the script branches per role. It answers with a `permissionDecision` of `allow` or `deny`;
a deny blocks the call and the reason is shown to the model.

Requires `python` (3.x) on `PATH`. Verified with Claude Code 2.1.306, Python 3.13, Windows 11 + Git Bash.

## Matrix

| Role (`agent_type`) | Read / Grep / Glob | Edit / Write | Bash / PowerShell | Agent / Skill / MCP |
|---|---|---|---|---|
| `test-writer` | deny anything under `src/main`; deny searches whose `path` contains a `src/main` (incl. project root) | only `src/test*` source sets | only `rm <file>` / `git rm <file>` with every file inside `src/test*`, named explicitly (no globs, dirs, flags other than `-f`/`-q`) | denied |
| `implementer` | deny any `src/*test*` source set; deny searches whose `path` contains one (incl. project root, `plugin/src`) | only `src/main`, `*.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties` | only `./gradlew …`, optional pipe into `head`/`tail`/`grep`; no `;`, `&&`, `>`, `$(…)`; command text may not mention test paths or `*Test.java`; plus `rm`/`git rm` of named files inside `src/main` | denied |
| `reviewer` | everything | **all denied** | `./gradlew …` and read-only git (`diff`, `log`, `status`, `show`, `blame`, `ls-files`, `grep`); same no-chaining rule | denied |
| main (no `agent_type`) and any other agent | everything | only `docs/specs/*.md`, `README.md`, `CHANGELOG.md`, `CLAUDE.md`, `.claude/**` | denied if the command has a write verb (`>`, `sed -i`, `tee`, `cp`, `mv`, `rm`, `python`, `node`, `Set-Content`, …) **and** mentions `src/`, `*.gradle.kts`, `libs.versions.toml` or `gradle.properties`; system `gradle` denied. Pure git command lines (add, commit incl. heredoc messages, push, log, diff...) are allowed, but git subcommands that rewrite the working tree (`checkout --`, `restore`, `stash`, `rm`, `mv`, `apply`, `clean`, `reset --hard`, `merge`, `rebase`, `pull`) are denied | allowed |

Layers, strongest first:

1. **Hook (mechanical, per tool call).** Path checks resolve the real path (Windows, POSIX and Git-Bash `/d/...` spellings), so
   relative paths, `..`, and symlinks are normalized before matching. Grep/Glob are denied when the search root is an ancestor of a
   forbidden source set, so a "search everything" cannot leak. Shell commands for restricted agents use an allowlist of the first command
   plus a ban on chaining and redirection.
2. **Tool allowlist in agent frontmatter.** `reviewer` has no Edit/Write; none of the three has Agent, Skill, Web or MCP tools.
   `omitClaudeMd: true` keeps the orchestrator's CLAUDE.md out of their context so they only know their own role.
3. **Prompt instructions** in each agent file restating the rule and telling the agent what to do when denied (stop and report, not route around).

## What this does NOT catch

- **Gradle output is trusted.** The implementer may read `plugin/build/test-results/**` and console output. Those contain test class and
  method names, assertion messages and stack traces (intended), but not test source. A test that prints its own source would leak it.
- **Test method names leak intent.** By design; the spec is supposed to carry the same information.
- A hook can only inspect the tool call it sees. If Claude Code ever adds a file-access tool with a different input key than
  `file_path` / `notebook_path` / `path` / `command`, extend `path_of()` and the tool sets at the top of the script.
- **Fail-closed only for the three roles.** If the script crashes, restricted agents are denied and the main instance is asked to confirm.
  If Python is missing, the hook errors are non-blocking and nothing is enforced; the frontmatter allowlists then remain as the only mechanical layer.
- Project hooks and project subagents only load once the folder is **trusted** in Claude Code. Hook config is snapshotted at session start,
  so after editing `settings.json` or this script, restart the session (or review via `/hooks`).

## Testing the guard

Pipe a fake event into the script:

```sh
echo '{"agent_type":"test-writer","tool_name":"Read","tool_input":{"file_path":"plugin/src/main/java/org/example/JsonPojoGenPlugin.java"},"cwd":"."}' \
  | CLAUDE_PROJECT_DIR="$PWD" python .claude/hooks/agent-guard.py
```

Expected: a JSON object with `"permissionDecision": "deny"`.
