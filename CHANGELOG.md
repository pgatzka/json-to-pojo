# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Spec-first TDD orchestrator setup for Claude Code: `CLAUDE.md`, `test-writer`, `implementer`
  and `reviewer` agents, path-isolation hooks, and `docs/specs/SPEC_TEMPLATE.md`.
- AssertJ 3.27.3 as a test dependency.

### Changed

- Moved the scaffold plugin from package `org.example` to `io.github.pgatzka`; plugin id is now
  `io.github.pgatzka.json-pojo-gen` (was `org.example.greeting`). The `greeting` task is unchanged apart from the id it prints.
  Spec: `docs/specs/package-migration.md`.
