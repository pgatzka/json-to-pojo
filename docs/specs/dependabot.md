# Feature: Dependabot for GitHub Actions and Gradle dependencies

## Context

`docs/specs/github-actions-ci.md` pins the third-party `gradle/actions/setup-gradle` action to a commit SHA and the
GitHub-owned actions to major tags; `docs/specs/build-tooling.md` pins plugin and library versions in the version
catalog. Without automation those pins go stale. Dependabot opens pull requests for new versions; CI validates them.

Decisions made here:

- Two ecosystems: `github-actions` (the workflow under `.github/workflows`) and `gradle` (`gradle/libs.versions.toml`,
  `build.gradle.kts`, `plugin/build.gradle.kts`, `settings.gradle.kts`). Dependabot does not update the Gradle wrapper.
- Weekly schedule, Monday 06:00 Europe/Berlin, so updates arrive at the start of the week.
- Updates are grouped per ecosystem into one PR each, to keep the PR count low.
- Commit message prefixes follow the repository's Conventional Commits: `ci` for actions, `build` for Gradle.
- At most 5 open PRs per ecosystem.

## Package and class names

No Java types. One new file, written by the implementer:

| File | Change |
|------|--------|
| `.github/dependabot.yml` | new |

## Public API (exact Java signatures)

None.

## File contract

`.github/dependabot.yml`, exactly this content:

```yaml
version: 2

updates:
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: Europe/Berlin
    open-pull-requests-limit: 5
    groups:
      github-actions:
        patterns:
          - "*"
    commit-message:
      prefix: ci

  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: Europe/Berlin
    open-pull-requests-limit: 5
    groups:
      gradle:
        patterns:
          - "*"
    commit-message:
      prefix: build
```

## Behavior / acceptance criteria

Dependabot runs on GitHub only; the criteria are checked by reading the file (reviewer) and a YAML parse (orchestrator).

1. `.github/dependabot.yml` exists and matches the contract byte for byte apart from trailing whitespace.
2. The file is valid YAML with `version: 2` and exactly two entries under `updates`.
3. The entries cover `github-actions` and `gradle`, both with `directory: /`, a weekly Monday schedule in `Europe/Berlin`,
   `open-pull-requests-limit: 5`, one catch-all group, and commit prefixes `ci` and `build` respectively.
4. No other file changes; `./gradlew check` stays green and up to date.

## Edge cases and error handling

- A Dependabot PR that breaks the build is caught by the `CI` workflow, which runs on every pull request.
- SHA-pinned actions with a trailing `# vX.Y.Z` comment are updated by Dependabot together with the comment.

## Out of scope

- Gradle wrapper updates (not supported by Dependabot; bump by hand with `./gradlew wrapper --gradle-version <v>`).
- Auto-merge of Dependabot PRs, reviewers/assignees, labels, ignore rules.
- Renovate or any other update bot.
