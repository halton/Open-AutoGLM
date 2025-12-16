---
description: Execute the implementation plan by processing and executing all tasks defined in tasks.md
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## Outline

1. Run `.specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks` from repo root and parse FEATURE_DIR and AVAILABLE_DOCS list. All paths must be absolute. For single quotes in args like "I'm Groot", use escape syntax: e.g 'I'\''m Groot' (or double-quote if possible: "I'm Groot").

2. **Check checklists status** (if FEATURE_DIR/checklists/ exists):
   - Scan all checklist files in the checklists/ directory
   - For each checklist, count:
     - Total items: All lines matching `- [ ]` or `- [X]` or `- [x]`
     - Completed items: Lines matching `- [X]` or `- [x]`
     - Incomplete items: Lines matching `- [ ]`
   - Create a status table:

     ```text
     | Checklist | Total | Completed | Incomplete | Status |
     |-----------|-------|-----------|------------|--------|
     | ux.md     | 12    | 12        | 0          | PASS |
     | test.md   | 8     | 5         | 3          | FAIL |
     | security.md | 6   | 6         | 0          | PASS |
     ```

   - Calculate overall status:
     - **PASS**: All checklists have 0 incomplete items
     - **FAIL**: One or more checklists have incomplete items

   - **If any checklist is incomplete**:
     - Display the table with incomplete item counts
     - **STOP** and ask: "Some checklists are incomplete. Do you want to proceed with implementation anyway? (yes/no)"
     - Wait for user response before continuing
     - If user says "no" or "wait" or "stop", halt execution
     - If user says "yes" or "proceed" or "continue", proceed to step 3

   - **If all checklists are complete**:
     - Display the table showing all checklists passed
     - Automatically proceed to step 3

3. Load and analyze the implementation context:
   - **REQUIRED**: Read tasks.md for the complete task list and execution plan
   - **REQUIRED**: Read plan.md for tech stack, architecture, and file structure
   - **IF EXISTS**: Read data-model.md for entities and relationships
   - **IF EXISTS**: Read contracts/ for API specifications and test requirements
   - **IF EXISTS**: Read research.md for technical decisions and constraints
   - **IF EXISTS**: Read quickstart.md for integration scenarios

4. **Project Setup Verification**:
   - **REQUIRED**: Create/verify ignore files based on actual project setup:

   **Detection & Creation Logic**:
   - Check if the following command succeeds to determine if the repository is a git repo (create/verify .gitignore if so):

     ```sh
     git rev-parse --git-dir 2>/dev/null
     ```

   - Check if Dockerfile* exists or Docker in plan.md -> create/verify .dockerignore
   - Check if .eslintrc* exists -> create/verify .eslintignore
   - Check if eslint.config.* exists -> ensure the config's `ignores` entries cover required patterns
   - Check if .prettierrc* exists -> create/verify .prettierignore
   - Check if .npmrc or package.json exists -> create/verify .npmignore (if publishing)
   - Check if terraform files (*.tf) exist -> create/verify .terraformignore
   - Check if .helmignore needed (helm charts present) -> create/verify .helmignore

   **If ignore file already exists**: Verify it contains essential patterns, append missing critical patterns only
   **If ignore file missing**: Create with full pattern set for detected technology

   **Common Patterns by Technology** (from plan.md tech stack):
   - **Node.js/JavaScript/TypeScript**: `node_modules/`, `dist/`, `build/`, `*.log`, `.env*`
   - **Python**: `__pycache__/`, `*.pyc`, `.venv/`, `venv/`, `dist/`, `*.egg-info/`
   - **Java**: `target/`, `*.class`, `*.jar`, `.gradle/`, `build/`
   - **C#/.NET**: `bin/`, `obj/`, `*.user`, `*.suo`, `packages/`
   - **Go**: `*.exe`, `*.test`, `vendor/`, `*.out`
   - **Ruby**: `.bundle/`, `log/`, `tmp/`, `*.gem`, `vendor/bundle/`
   - **PHP**: `vendor/`, `*.log`, `*.cache`, `*.env`
   - **Rust**: `target/`, `debug/`, `release/`, `*.rs.bk`, `*.rlib`, `*.prof*`, `.idea/`, `*.log`, `.env*`
   - **Kotlin**: `build/`, `out/`, `.gradle/`, `.idea/`, `*.class`, `*.jar`, `*.iml`, `*.log`, `.env*`
   - **C++**: `build/`, `bin/`, `obj/`, `out/`, `*.o`, `*.so`, `*.a`, `*.exe`, `*.dll`, `.idea/`, `*.log`, `.env*`
   - **C**: `build/`, `bin/`, `obj/`, `out/`, `*.o`, `*.a`, `*.so`, `*.exe`, `Makefile`, `config.log`, `.idea/`, `*.log`, `.env*`
   - **Swift**: `.build/`, `DerivedData/`, `*.swiftpm/`, `Packages/`
   - **R**: `.Rproj.user/`, `.Rhistory`, `.RData`, `.Ruserdata`, `*.Rproj`, `packrat/`, `renv/`
   - **Universal**: `.DS_Store`, `Thumbs.db`, `*.tmp`, `*.swp`, `.vscode/`, `.idea/`

   **Tool-Specific Patterns**:
   - **Docker**: `node_modules/`, `.git/`, `Dockerfile*`, `.dockerignore`, `*.log*`, `.env*`, `coverage/`
   - **ESLint**: `node_modules/`, `dist/`, `build/`, `coverage/`, `*.min.js`
   - **Prettier**: `node_modules/`, `dist/`, `build/`, `coverage/`, `package-lock.json`, `yarn.lock`, `pnpm-lock.yaml`
   - **Terraform**: `.terraform/`, `*.tfstate*`, `*.tfvars`, `.terraform.lock.hcl`
   - **Kubernetes/k8s**: `*.secret.yaml`, `secrets/`, `.kube/`, `kubeconfig*`, `*.key`, `*.crt`

5. Parse tasks.md structure and extract:
   - **Task phases**: Setup, Foundational, User Stories, Polish
   - **Task dependencies**: Sequential vs parallel execution rules
   - **Task details**: ID, description, file paths, parallel markers [P], story labels [USn]
   - **Execution flow**: Order and dependency requirements

6. **SUBAGENT EXECUTION MODE** - Execute implementation using subagents:

   ### 6.1 Parallel Task Execution with Subagents

   For tasks marked with [P] that can run in parallel:

   ```
   Use the Task tool to spawn multiple subagents simultaneously:

   - subagent_type: "general-purpose" for most implementation tasks
   - subagent_type: "mobile-developer" for Android/iOS specific tasks
   - subagent_type: "frontend-developer" for UI/Compose tasks
   - subagent_type: "backend-architect" for service layer tasks

   Example - Launch 3 parallel entity creation tasks:
   Task(subagent_type="general-purpose", prompt="Implement T008: Create Task entity...")
   Task(subagent_type="general-purpose", prompt="Implement T009: Create Action entity...")
   Task(subagent_type="general-purpose", prompt="Implement T010: Create ActionResult...")
   ```

   **Parallel Execution Rules**:
   - Group all [P] marked tasks in the same phase
   - Launch up to 5 subagents simultaneously using multiple Task tool calls in a single message
   - Wait for all parallel tasks to complete before proceeding
   - Use `run_in_background: true` for long-running tasks, then collect with TaskOutput

   ### 6.2 Sequential Task Execution

   For tasks WITHOUT [P] marker:
   - Execute in the main conversation sequentially
   - Or use a single subagent per task if complex
   - Wait for completion before starting next task

   ### 6.3 Phase Completion Gates

   After completing each phase, run quality gates using specialized subagents:

   **Code Review Gate** (after each phase with code changes):
   ```
   Task(
     subagent_type="code-reviewer",
     prompt="Review all code changes in this phase for:
       - Code quality and best practices
       - Potential bugs or issues
       - Consistency with plan.md architecture
       - Security vulnerabilities
       Files changed: [list files from completed tasks]"
   )
   ```

   **Architecture Review Gate** (after Foundational and each User Story phase):
   ```
   Task(
     subagent_type="architect-review",
     prompt="Validate implementation against plan.md architecture:
       - Module separation is maintained
       - Dependencies flow correctly
       - No circular dependencies
       - Interfaces match contracts/"
   )
   ```

   ### 6.4 Commit After Task Completion

   After each task (or logical group) is completed:
   1. Mark task as complete in tasks.md: `- [X] Txxx ...`
   2. Stage and commit changes:
      ```
      git add -A
      git commit -m "feat(component): task description

      Task: Txxx
      Phase: [phase name]

      [Generated with Claude Code]

      Co-Authored-By: Claude <noreply@anthropic.com>"
      ```

7. Implementation execution flow:

   ```
   Phase 1: Setup
   ├── Execute T001-T006 (sequential, main agent)
   ├── Commit: "chore: project setup complete"
   └── Gate: None (setup only)

   Phase 2: Foundational
   ├── 2.1 Data Layer: T007-T022
   │   ├── T007 (sequential - enums first)
   │   ├── T008-T015 [P] → Launch 8 subagents in parallel
   │   ├── Wait for all entities
   │   ├── T016-T020 [P] → Launch 5 subagents for DAOs
   │   ├── T021-T022 (sequential - depends on DAOs)
   │   └── Commit: "feat(data): add Room entities and DAOs"
   ├── 2.2-2.6: Similar pattern...
   ├── Gate: architect-review subagent
   └── Commit: "feat: foundational infrastructure complete"

   Phase 3+: User Stories
   ├── For each User Story phase:
   │   ├── Group [P] tasks → parallel subagents
   │   ├── Sequential tasks → main agent or single subagent
   │   ├── Gate: code-reviewer subagent
   │   └── Commit: "feat(USn): [story description]"

   Phase N: Polish
   ├── Execute polish tasks
   ├── Gate: code-reviewer + architect-review
   └── Final commit
   ```

8. Progress tracking and error handling:
   - Report progress after each completed task
   - Track subagent status using TaskOutput for background tasks
   - Halt execution if any non-parallel task fails
   - For parallel tasks [P], continue with successful tasks, report failed ones
   - If a subagent fails, capture error and decide:
     - Retry with debugger subagent: `Task(subagent_type="debugger", prompt="...")`
     - Skip and continue (if non-critical)
     - Halt and report (if blocking)
   - **IMPORTANT**: Mark completed tasks as [X] in tasks.md immediately

9. Completion validation:
   - Verify all required tasks are completed
   - Run final code review: `Task(subagent_type="code-reviewer", prompt="Final review...")`
   - Run final architecture review: `Task(subagent_type="architect-review", prompt="...")`
   - Check that implemented features match the original specification
   - Validate that tests pass (if applicable)
   - Report final status with summary:
     ```
     ## Implementation Complete

     | Phase | Tasks | Status |
     |-------|-------|--------|
     | Setup | 6/6 | Complete |
     | Foundational | 36/36 | Complete |
     | US1 | 22/22 | Complete |
     ...

     Total: X tasks completed
     Commits: Y commits made
     Reviews: Z code reviews passed
     ```

## Subagent Selection Guide

| Task Type | Subagent | When to Use |
|-----------|----------|-------------|
| General implementation | `general-purpose` | Most coding tasks |
| Android/Kotlin code | `mobile-developer` | UI, Android-specific |
| UI Components | `frontend-developer` | Compose, UI logic |
| Service layer | `backend-architect` | Services, APIs |
| Code review | `code-reviewer` | After phase completion |
| Architecture check | `architect-review` | After major phases |
| Debug failures | `debugger` | When tasks fail |
| Explore codebase | `Explore` | Finding related code |

## Notes

- This command uses the Task tool to spawn specialized subagents
- Parallel tasks [P] are executed simultaneously using multiple subagents
- Quality gates (code-review, architecture-review) run after each phase
- Each task completion triggers a commit with task ID reference
- If tasks.md is incomplete, suggest running `/speckit.tasks` first
