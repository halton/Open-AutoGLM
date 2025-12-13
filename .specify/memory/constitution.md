<!--
SYNC IMPACT REPORT
==================
Version Change: 0.0.0 → 1.0.0
Bump Type: MAJOR (initial constitution creation)

Modified Principles: N/A (initial creation)

Added Sections:
- I. Code Quality & Architecture
- II. Documentation Standards
- III. Simplicity & Maintainability
- Development Workflow
- Quality Gates
- Governance

Removed Sections: N/A (initial creation)

Templates Status:
- .specify/templates/plan-template.md: ✅ compatible (generic Constitution Check)
- .specify/templates/spec-template.md: ✅ compatible (no constitution refs)
- .specify/templates/tasks-template.md: ✅ compatible (no constitution refs)
- .specify/templates/checklist-template.md: ✅ compatible (no constitution refs)
- .specify/templates/agent-file-template.md: ✅ compatible (no constitution refs)

Deferred Items: None
==================
-->

# Open-AutoGLM Constitution

## Core Principles

### I. Code Quality & Architecture

All code contributions MUST adhere to the established architecture patterns of the
Open-AutoGLM framework:

- **Module Separation**: Agent logic (`phone_agent/agent.py`), ADB operations
  (`phone_agent/adb/`), model client (`phone_agent/model/`), and actions
  (`phone_agent/actions/`) MUST remain distinct and loosely coupled.
- **OpenAI-Compatible API**: Model client MUST maintain compatibility with the OpenAI
  API format to support multiple backend providers (vLLM, SGLang, BigModel, ModelScope).
- **Configuration-Driven**: App mappings, prompts, and model settings MUST be
  configurable without code changes. Use `phone_agent/config/` for all configuration.
- **Type Hints**: All public functions and class methods MUST include Python type hints
  for parameters and return values.
- **Error Handling**: ADB operations MUST handle connection failures gracefully and
  provide actionable error messages in both Chinese and English.

**Rationale**: The framework supports multiple model providers and deployment scenarios.
Clean separation ensures new providers can be added without modifying core agent logic.

### II. Documentation Standards

Documentation is a first-class deliverable, not an afterthought:

- **Bilingual Requirement**: All user-facing documentation MUST be provided in both
  Chinese (primary, `README.md`) and English (`README_en.md`).
- **Deployment Guides**: Any change affecting deployment MUST update the corresponding
  sections in both README files, including:
  - Environment setup steps
  - Model service configuration
  - ADB connection troubleshooting
- **API Documentation**: Public Python APIs (`PhoneAgent`, `ModelConfig`, `AgentConfig`,
  `ADBConnection`) MUST have docstrings with usage examples.
- **AI-Friendly Section**: The "Automated Deployment Guide for AI" section MUST be kept
  current for AI-assisted deployment workflows.
- **Code Comments**: Non-obvious logic, especially in action parsing and screen
  understanding, MUST include inline comments explaining the intent.

**Rationale**: Open-AutoGLM targets both Chinese and international users. Clear
documentation reduces support burden and enables autonomous AI deployment assistance.

### III. Simplicity & Maintainability

Prefer simple, working solutions over complex, theoretical ones:

- **YAGNI (You Aren't Gonna Need It)**: Do not add features, abstractions, or
  configuration options until there is a concrete use case.
- **Single Responsibility**: Each module/class/function SHOULD do one thing well.
  Avoid "god objects" that handle multiple unrelated concerns.
- **Minimal Dependencies**: New external dependencies MUST be justified. Prefer
  standard library solutions when the overhead of a dependency outweighs benefits.
- **Backward Compatibility**: Changes to public APIs MUST maintain backward
  compatibility or follow a deprecation cycle with clear migration guides.
- **Readable Over Clever**: Code MUST be readable by developers unfamiliar with the
  codebase. Avoid clever tricks that sacrifice clarity.

**Rationale**: The project is open-source and designed for external contributors.
Simple code is easier to understand, review, maintain, and extend.

## Development Workflow

### Code Review Requirements

- All pull requests MUST be reviewed by at least one maintainer before merge.
- PRs affecting core agent logic (`phone_agent/agent.py`) or ADB operations
  (`phone_agent/adb/`) require additional scrutiny for edge cases.
- Documentation-only PRs may have expedited review.

### Testing Expectations

- New features SHOULD include corresponding test cases in `tests/`.
- Bug fixes SHOULD include a regression test demonstrating the fix.
- Manual testing on physical Android device is RECOMMENDED for ADB-related changes.

### Commit Message Format

- Use conventional commits format: `type: description`
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Reference issues when applicable: `feat: add support for new app (#123)`

## Quality Gates

Before merging any PR:

1. **Linting**: Code passes linting checks (if configured).
2. **Tests**: Existing tests pass (`pytest tests/`).
3. **Documentation**: Bilingual documentation updated if user-facing behavior changes.
4. **Compatibility**: No breaking changes to public APIs without deprecation notice.

## Governance

This constitution supersedes all other development practices for Open-AutoGLM.

### Amendment Process

1. Propose changes via GitHub Issue with `[Constitution]` prefix.
2. Discuss with maintainers and community.
3. Submit PR modifying this file with clear rationale.
4. Requires approval from at least two maintainers.
5. Update version according to semantic versioning:
   - MAJOR: Principle removal or fundamental redefinition
   - MINOR: New principle or significant expansion
   - PATCH: Clarifications, wording improvements

### Compliance

- All PRs and code reviews SHOULD verify adherence to these principles.
- Complexity deviating from Principle III MUST be documented and justified.
- Use `.specify/memory/constitution.md` as the single source of truth.

**Version**: 1.0.0 | **Ratified**: 2025-12-12 | **Last Amended**: 2025-12-12
