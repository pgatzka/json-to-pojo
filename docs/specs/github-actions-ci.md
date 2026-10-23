# Feature: GitHub Actions CI — test, format check, Sonar, publish

## Context

With `docs/specs/build-tooling.md` in place, the build offers `check` (tests + `spotlessCheck` + JaCoCo report), `sonar`
and `publish`. This spec adds the GitHub Actions workflow that runs them: every push to `main` and every pull request is
built and tested; Sonar runs when a token is configured; pushes to `main` also publish the snapshot to GitHub Packages.

Decisions made here (user chose: publish on every push to `main`; SonarCloud):

- One workflow file, two jobs. `publish` depends on `build` and runs only for pushes to `main`.
- JDK: Temurin 25 via `actions/setup-java`, so the toolchain is found locally and nothing is downloaded by foojay in CI.
- Gradle: `gradle/actions/setup-gradle` for wrapper validation and caching.
- The Sonar step is skipped when the `SONAR_TOKEN` secret is absent (also true for pull requests from forks), so CI is green
  before the SonarCloud project exists. `fetch-depth: 0` so Sonar can compute new code.
- Test and coverage reports are uploaded as an artifact only when the build fails.
- Action versions are pinned to major tags: `actions/checkout@v5`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v5`,
  `actions/upload-artifact@v4`.
- Workflow-level `permissions: contents: read`; the `publish` job adds `packages: write`.

## Package and class names

No Java types. One new file, written by the implementer:

| File | Change |
|------|--------|
| `.github/workflows/ci.yml` | new |

## Public API (exact Java signatures)

None.

## Workflow contract

`.github/workflows/ci.yml`, exactly this content:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Build, test, analyze
    runs-on: ubuntu-latest
    env:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    steps:
      - name: Check out
        uses: actions/checkout@v5
        with:
          fetch-depth: 0

      - name: Set up JDK 25
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 25

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Check (tests, formatting, coverage)
        run: ./gradlew check

      - name: Sonar analysis
        if: env.SONAR_TOKEN != ''
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: ./gradlew sonar

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: reports
          path: plugin/build/reports/
          if-no-files-found: ignore

  publish:
    name: Publish to GitHub Packages
    needs: build
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - name: Check out
        uses: actions/checkout@v5

      - name: Set up JDK 25
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 25

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Publish
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: ./gradlew publish
```

## Behavior / acceptance criteria

GitHub Actions cannot run locally; the criteria are checked by reading the file (reviewer) and by a YAML parse (orchestrator).

1. `.github/workflows/ci.yml` exists and matches the contract above byte for byte apart from trailing whitespace.
2. The file is valid YAML (parses with a YAML 1.1/1.2 parser).
3. Triggers are exactly `push` to `main` and `pull_request`.
4. The `build` job runs, in order: checkout (full history), Temurin 25, Gradle setup, `./gradlew check`, conditional
   `./gradlew sonar`, conditional report upload.
5. The Sonar step's condition is `env.SONAR_TOKEN != ''` with `SONAR_TOKEN` set at **job** level from `secrets.SONAR_TOKEN`
   (a step cannot see its own `env` in its `if`).
6. The `publish` job needs `build`, runs only for `push` events on `refs/heads/main`, has `packages: write`, and runs
   `./gradlew publish` with `GITHUB_ACTOR` and `GITHUB_TOKEN` in its environment.
7. Workflow-level permissions are read-only (`contents: read`).
8. No other workflow files are added; no secrets other than `SONAR_TOKEN` and the built-in `GITHUB_TOKEN` are referenced.

## Edge cases and error handling

- Pull request from a fork: `secrets.SONAR_TOKEN` is empty, the Sonar step is skipped, everything else runs.
- `check` failure: the Sonar step does not run (previous step failed), the reports artifact is uploaded, `publish` is skipped.
- Concurrent pushes to the same ref cancel the older run (`concurrency`).

## Out of scope

- Dependabot or Renovate configuration for action or dependency updates.
- Release workflows, tags, GitHub Releases, changelog automation.
- Matrix builds (other OSes or JDKs), caching tweaks beyond `setup-gradle` defaults.
- Branch protection rules (configured in the repository settings, not in this file).
