#!/usr/bin/env python3
"""
PreToolUse guard for the spec-first TDD setup in this repository.

Wired from .claude/settings.json with matcher "*". Claude Code pipes a JSON
object on stdin that includes tool_name, tool_input, cwd and, when the call is
made by a subagent, agent_type (the subagent's name).

Roles enforced (see .claude/hooks/README.md for the full matrix):

  test-writer   may read docs/specs, test source sets and build scripts; may write
                only under src/test* source sets; must never see src/main.
  implementer   may read docs/specs, src/main and build scripts; may write only
                under src/main (plus build scripts); must never see any test
                source set. Shell is limited to ./gradlew.
  reviewer      read-only: no Edit/Write; shell limited to ./gradlew and read-only git.
  main / other  may edit only docs/specs/**, README.md, CHANGELOG.md, CLAUDE.md, .claude/**.

Decision protocol: print a PreToolUse JSON decision on stdout and exit 0.
If the script itself crashes, restricted agents are denied and everyone else
is asked (fail closed where it matters).
"""
import json
import os
import re
import sys

RESTRICTED = {"test-writer", "implementer", "reviewer"}

# Source-set zones, matched against a lower-case, forward-slash, project-relative path.
PROD_RE = re.compile(r"(^|/)src/main(/|$)")
TEST_RE = re.compile(r"(^|/)src/(test|[a-z0-9_\-]*test)(/|$)")
BUILD_SCRIPT_RE = re.compile(r"(^|/)([a-z0-9_\-]+\.gradle(\.kts)?|gradle/libs\.versions\.toml|gradle\.properties)$")
DOCS_RE = re.compile(r"^(docs/specs/[^/]+\.md|readme\.md|changelog\.md|claude\.md|\.claude(/.*)?)$")

# Text patterns for shell commands (Bash and PowerShell tools).
TEST_TEXT_RE = re.compile(r"src[\\/]+[a-z0-9_\-]*test|[A-Za-z0-9_]*Tests?\.java", re.I)
GRADLEW_RE = re.compile(r"^\s*(?:\.[\\/]|\.\\)?gradlew(?:\.bat)?(\s|$)", re.I)
GIT_RO_RE = re.compile(
    r"^\s*git\s+(diff|log|status|show|blame|ls-files|grep|rev-parse|branch(\s+(--list|-a|-r))?|remote\s+-v)(\s|$)",
    re.I,
)
REVIEWER_FIRST_RE = re.compile(f"(?:{GRADLEW_RE.pattern})|(?:{GIT_RO_RE.pattern})", re.I)
# Things no restricted agent may do in a shell: chaining, redirection, substitution.
SHELL_ESCAPE_RE = re.compile(r"(;|&&|\|\||>|<|`|\$\(|\$\{|\n)")
# Pipes are tolerated only into these output filters.
PIPE_OK_RE = re.compile(
    r"^\s*(head|tail|grep|findstr|wc|sort|uniq|cut|tr|cat|Select-Object|Select-String|Out-String|Measure-Object)(\s|$)",
    re.I,
)
WRITE_VERB_RE = re.compile(
    r"(>|\bsed\s+-i|\btee\b|\bcp\b|\bmv\b|\brm\b|\btouch\b|\bmkdir\b|\bpython3?\b|\bnode\b|\bperl\b|\bruby\b"
    r"|Set-Content|Out-File|Add-Content|New-Item|Remove-Item|Copy-Item|Move-Item"
    r"|\bgit\s+checkout\s+--|\bgit\s+restore\b|\bgit\s+stash\b)",
    re.I,
)
CODE_TEXT_RE = re.compile(r"src[\\/]|\.gradle\.kts|libs\.versions\.toml|gradle\.properties", re.I)
# git subcommands that rewrite the working tree; everything else in git (add, commit, push, log, ...) is fine for the orchestrator.
GIT_CMD_RE = re.compile(r"^\s*git(\s|$)")
GIT_WRITE_RE = re.compile(
    r"\bgit\s+(-\S+\s+)*(checkout\s+(\S+\s+)?--|checkout\s+(-f|--force)|restore|stash|rm|mv|apply|clean|reset\s+--(hard|merge)|revert|cherry-pick|rebase|merge|pull)(?=\s|$)",
    re.I,
)
SYSTEM_GRADLE_RE = re.compile(r"(^|[\s;&|])gradle(\s|$)", re.I)

READ_TOOLS = {"Read", "Grep", "Glob"}
SEARCH_TOOLS = {"Grep", "Glob"}
WRITE_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
SHELL_TOOLS = {"Bash", "PowerShell"}
DELEGATION_TOOLS = {"Agent", "Task", "Skill", "WebFetch", "WebSearch", "Workflow"}
# Tools that carry no file or shell access and are harmless for restricted agents.
HARMLESS_TOOLS = {"TodoWrite", "TaskOutput", "SubagentHandback", "AskUserQuestion", "SendFeedback"}


def emit(decision, reason=None):
    body = {"hookEventName": "PreToolUse", "permissionDecision": decision}
    if reason:
        body["permissionDecisionReason"] = reason
    sys.stdout.write(json.dumps({"hookSpecificOutput": body}))
    sys.stdout.flush()
    sys.exit(0)


def deny(reason):
    emit("deny", "agent-guard: " + reason)


def allow():
    emit("allow")


def to_native(p):
    """Accept Windows, POSIX and Git-Bash (/d/...) spellings."""
    m = re.match(r"^/+([a-zA-Z])/(.*)$", p or "")
    if m and os.name == "nt":
        return f"{m.group(1).upper()}:/{m.group(2)}"
    return p


def rel(path, root, cwd):
    """Project-relative, lower-case, forward-slash path. None if outside the project."""
    path = to_native(path)
    if not os.path.isabs(path):
        path = os.path.join(cwd, path)
    path = os.path.realpath(path)
    try:
        r = os.path.relpath(path, root)
    except ValueError:  # different drive on Windows
        return None
    r = r.replace("\\", "/")
    if r == ".":
        return ""
    if r == ".." or r.startswith("../"):
        return None
    return r.lower()


def dir_contains_zone(abs_dir, zone_re, root):
    """True if any directory at or below abs_dir (pruned) matches zone_re."""
    if not os.path.isdir(abs_dir):
        return False
    prune = {".git", ".gradle", "build", "node_modules", ".idea", ".claude"}
    for dirpath, dirnames, _ in os.walk(abs_dir):
        dirnames[:] = [d for d in dirnames if d not in prune]
        for d in dirnames:
            r = os.path.relpath(os.path.join(dirpath, d), root).replace("\\", "/").lower()
            if zone_re.search(r):
                return True
    return False


def shell_is_safe(cmd, first_ok_re):
    """First segment must match first_ok_re; no chaining/redirection; pipes only into filters."""
    if SHELL_ESCAPE_RE.search(cmd):
        return False, "no command chaining, redirection or substitution is allowed for this agent"
    segments = cmd.split("|")
    if not first_ok_re.search(segments[0]):
        return False, "only the allowed command(s) may be run (see agent definition)"
    for seg in segments[1:]:
        if not PIPE_OK_RE.search(seg):
            return False, f"pipe target not allowed: {seg.strip()[:40]}"
    return True, None


DELETE_FLAGS = {"-f", "--force", "-q", "--quiet", "--"}


def delete_is_safe(cmd, zone_re, root, cwd):
    """`rm <files>` or `git rm <files>` where every file resolves inside zone_re and is a regular file.
    No recursion flags, no chaining, no globs."""
    if SHELL_ESCAPE_RE.search(cmd) or "|" in cmd:
        return False, "no chaining, piping or redirection in a delete command"
    try:
        import shlex
        tokens = shlex.split(cmd, posix=True)
    except ValueError as exc:
        return False, f"cannot parse command: {exc}"
    if tokens[:2] == ["git", "rm"]:
        rest = tokens[2:]
    elif tokens[:1] == ["rm"]:
        rest = tokens[1:]
    else:
        return False, "not a delete command"
    paths = []
    for t in rest:
        if t in DELETE_FLAGS:
            continue
        if t.startswith("-"):
            return False, f"flag not allowed in delete: {t}"
        if any(ch in t for ch in "*?["):
            return False, "globs are not allowed in delete; name each file"
        paths.append(t)
    if not paths:
        return False, "no files named"
    for p in paths:
        r = rel(p, root, cwd)
        if r is None or not zone_re.search(r):
            return False, f"may delete only files inside your own writable source set: {p}"
        abs_p = os.path.join(root, r)
        if os.path.exists(abs_p) and not os.path.isfile(abs_p):
            return False, f"may delete only regular files, not directories: {p}"
    return True, None


def strip_heredocs(cmd):
    """Remove heredoc bodies (<<EOF ... EOF) so a commit message is not mistaken for shell commands."""
    lines = cmd.split("\n")
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        m = re.search(r"<<-?\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\1", line)
        out.append(line)
        i += 1
        if m:
            term = m.group(2)
            while i < len(lines) and lines[i].strip() != term:
                i += 1
            i += 1  # skip terminator line
    return "\n".join(out)


def path_of(ti):
    for key in ("file_path", "notebook_path", "path"):
        if ti.get(key):
            return ti[key]
    return None


def guard_file_access(agent, tool, ti, root, cwd, forbidden_re, forbidden_name, write_ok_re, write_hint, search_hint):
    """Shared Read/Grep/Glob/Edit/Write logic for test-writer and implementer."""
    p = path_of(ti)
    if p:
        r = rel(p, root, cwd)
    else:
        r = "" if tool in SEARCH_TOOLS else None
    if p and r is None:
        # Outside the project tree (e.g. Gradle caches, JDK sources).
        if tool in WRITE_TOOLS:
            deny(write_hint)
        allow()
    if r is not None and forbidden_re.search(r):
        deny(f"{agent} must never read or write {forbidden_name}. {search_hint}")
    if tool in SEARCH_TOOLS:
        abs_dir = os.path.join(root, r) if r else root
        if dir_contains_zone(abs_dir, forbidden_re, root):
            deny(f"{agent}: this search would include {forbidden_name}. {search_hint}")
    if tool in WRITE_TOOLS and (r is None or not write_ok_re.search(r)):
        deny(write_hint)
    allow()


def decide(data):
    agent = data.get("agent_type") or ""
    tool = data.get("tool_name") or ""
    ti = data.get("tool_input") or {}
    if not isinstance(ti, dict):
        ti = {}
    root = os.path.realpath(os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd())
    cwd = os.path.realpath(data.get("cwd") or root)

    if not tool:
        if agent in RESTRICTED:
            deny("malformed hook input for a restricted agent")
        allow()

    # ------------------------------------------------------------------ test-writer
    if agent == "test-writer":
        if tool in SHELL_TOOLS:
            cmd = ti.get("command") or ""
            ok, why = delete_is_safe(cmd, TEST_RE, root, cwd)
            if not ok:
                deny("test-writer may use the shell only to delete obsolete test files (`rm <file>` / `git rm <file>` under "
                     f"plugin/src/test or plugin/src/functionalTest); no Gradle, no reading: {why}")
            allow()
        if tool in DELEGATION_TOOLS or tool.startswith("mcp__"):
            deny(f"test-writer may not use {tool}.")
        if tool in READ_TOOLS | WRITE_TOOLS:
            guard_file_access(
                agent, tool, ti, root, cwd,
                forbidden_re=PROD_RE,
                forbidden_name="production code (src/main)",
                write_ok_re=TEST_RE,
                write_hint="test-writer may write only under plugin/src/test or plugin/src/functionalTest.",
                search_hint="Work only from the spec in docs/specs. Set `path` to docs/specs, plugin/src/test or plugin/src/functionalTest.",
            )
        if tool in HARMLESS_TOOLS:
            allow()
        deny(f"test-writer may not use {tool}.")

    # ------------------------------------------------------------------ implementer
    if agent == "implementer":
        if tool in SHELL_TOOLS:
            cmd = ti.get("command") or ""
            if TEST_TEXT_RE.search(cmd):
                deny("implementer must never read test sources. Read the Gradle failure output instead "
                     "(assertion messages and stack traces are fine).")
            ok, why = shell_is_safe(cmd, GRADLEW_RE)
            if not ok:
                ok, why = delete_is_safe(cmd, PROD_RE, root, cwd)
            if not ok:
                deny("implementer may run only ./gradlew commands (optionally piped into head/tail/grep) or delete "
                     f"obsolete files under plugin/src/main (`rm <file>` / `git rm <file>`): {why}")
            allow()
        if tool in DELEGATION_TOOLS or tool.startswith("mcp__"):
            deny(f"implementer may not use {tool}.")
        if tool in READ_TOOLS | WRITE_TOOLS:
            guard_file_access(
                agent, tool, ti, root, cwd,
                forbidden_re=TEST_RE,
                forbidden_name="test sources (src/test, src/functionalTest)",
                write_ok_re=re.compile(f"(?:{PROD_RE.pattern})|(?:{BUILD_SCRIPT_RE.pattern})"),
                write_hint="implementer may write only under plugin/src/main (and build scripts when the spec requires a dependency).",
                search_hint="Read the Gradle failure output instead. Set `path` to docs/specs or plugin/src/main.",
            )
        if tool in HARMLESS_TOOLS:
            allow()
        deny(f"implementer may not use {tool}.")

    # ------------------------------------------------------------------ reviewer
    if agent == "reviewer":
        if tool in WRITE_TOOLS:
            deny("reviewer is read-only. Report findings; do not change files.")
        if tool in SHELL_TOOLS:
            cmd = ti.get("command") or ""
            ok, why = shell_is_safe(cmd, REVIEWER_FIRST_RE)
            if not ok:
                deny(f"reviewer may run only ./gradlew and read-only git commands: {why}")
            allow()
        if tool in DELEGATION_TOOLS or tool.startswith("mcp__"):
            deny(f"reviewer may not use {tool}.")
        if tool in READ_TOOLS or tool in HARMLESS_TOOLS:
            allow()
        deny(f"reviewer may not use {tool}.")

    # ------------------------------------------------------------- main / any other agent
    # The orchestrator (no agent_type) and any helper agent that is not one of the
    # three roles may only edit docs/specs/*.md, README.md, CHANGELOG.md and .claude/**.
    who = agent or "the orchestrator"
    if tool in WRITE_TOOLS:
        p = path_of(ti)
        r = rel(p, root, cwd) if p else None
        if p and r is None:
            allow()  # outside the project tree (scratchpad, home config): not our concern
        if r is None or not DOCS_RE.search(r):
            deny(f"{who} may edit only docs/specs/*.md, README.md, CHANGELOG.md, CLAUDE.md and .claude/**. "
                 "Code, tests and build scripts are written by the implementer / test-writer agents (see CLAUDE.md).")
        allow()
    if tool in SHELL_TOOLS:
        cmd = ti.get("command") or ""
        if GIT_CMD_RE.search(cmd):
            # A command line made only of git commands (chained, piped into filters, heredoc commit messages allowed).
            head = strip_heredocs(cmd)
            segments = [s for s in re.split(r"&&|\|\||;|\||\n", head) if s.strip()]
            head_no_hd = re.sub(r"<<-?\s*['\"]?\w+['\"]?", "", head)
            pure_git = segments and all(GIT_CMD_RE.search(s) or PIPE_OK_RE.search(s) for s in segments) \
                and not re.search(r"[><`]|\$\(", head_no_hd)
            if pure_git:
                if GIT_WRITE_RE.search(head):
                    deny(f"{who} may not rewrite the working tree with git (checkout --, restore, stash, rm, mv, apply, clean, "
                         "reset --hard, merge, rebase, pull...). Commit, push and inspect only; delegate code changes to the agents.")
                allow()
        if WRITE_VERB_RE.search(cmd) and CODE_TEXT_RE.search(cmd):
            deny(f"{who} may not modify source or build files from the shell. "
                 "Delegate to the implementer / test-writer agents (see CLAUDE.md).")
        if SYSTEM_GRADLE_RE.search(cmd) and not GRADLEW_RE.search(cmd):
            deny("use the wrapper (./gradlew), never a system gradle.")
        allow()
    allow()


if __name__ == "__main__":
    raw = sys.stdin.read()
    try:
        data = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        data = {}
    if not isinstance(data, dict):
        data = {}
    try:
        decide(data)
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001 - fail closed where it matters
        sys.stderr.write(f"agent-guard error: {exc}\n")
        if (data.get("agent_type") or "") in RESTRICTED:
            emit("deny", f"agent-guard crashed ({exc}); tool call blocked for a restricted agent.")
        emit("ask", f"agent-guard crashed ({exc}); confirm manually.")
