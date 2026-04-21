# Codex Rules

## Style

- Do not use emojis in source code, comments, documentation, commit messages, or PR descriptions.
- Use plain, professional text only.

## Shared Codex Skills

- This repository exposes shared Codex skills through `.agents/skills/` and shared custom agents through `.codex/agents/`.
- In this working copy, both directories are linked to the sibling repository `../ai-skills`.
- Treat `../ai-skills` as the source of truth for shared skill content unless the user explicitly asks for a repo-specific override here.
- `.codex/config.toml` is copied locally so this repository can keep its own Codex project settings if needed.
- If the shared repository moves or the links are missing, recreate them with `scripts/setup-shared-codex-skills.ps1`.

## Branching And Release Flow

This repository uses a single-branch delivery model centered on `main`.

### Main Branch

- `main` is both the active development branch and the production branch.
- `main` must always stay releasable.
- Direct commits and direct pushes to `main` are allowed.

### Optional Short-Lived Branches

Short-lived branches are optional and may be used when they help isolate work.

- `feature/<short-description>` for new functionality
- `fix/<short-description>` for bug fixes
- `refactor/<short-description>` for internal improvements without behavior changes
- `docs/<short-description>` for documentation-only work
- `test/<short-description>` for test-only work
- `chore/<short-description>` for maintenance tasks

Use lowercase kebab-case names.

## Pull Request Policy

- Pull Requests are optional for this repository.
- If a Pull Request is used, its title must follow Conventional Commits.
- If a Pull Request is squash-merged, ensure the PR title is the final Conventional Commit message that should stay in history.
- Do not merge or push changes if required checks are failing.

Codex may create, review, merge, or skip Pull Requests when that best fits the current task and repository settings.

## Conventional Commits

Every commit and every PR title must use Conventional Commits.

Format:

`type(scope): short summary`

Examples:

- `feat(scan): add folder prefilter before duplicate scan`
- `fix(trash): prevent restore when source path is missing`
- `refactor(data): simplify MediaStore cursor mapping`
- `docs(workflow): document direct-to-main release process`

Allowed types:

- `feat`
- `fix`
- `refactor`
- `perf`
- `test`
- `docs`
- `build`
- `ci`
- `chore`
- `revert`

Recommended scopes for this project:

- `app`
- `scan`
- `duplicates`
- `quality`
- `trash`
- `settings`
- `home`
- `navigation`
- `ui`
- `domain`
- `data`
- `db`
- `di`
- `build`
- `deps`
- `docs`
- `workflow`
- `release`

Breaking changes must use `!` or a `BREAKING CHANGE:` footer.

## Versioning Policy

This project uses Semantic Versioning for `versionName` and a monotonically increasing Android `versionCode`.

### versionName

- `versionName` must always use the full `MAJOR.MINOR.PATCH` format.
- Example: `1.0.0`, not `1.0`.
- User-facing versions must stay clean and release-like.
- Do not append `-debug`, `-dev`, `-test`, or similar suffixes to the user-visible `versionName`.
- Debug or internal differentiation must use other mechanisms such as `applicationIdSuffix`, build metadata, branch names, or release notes.
- Only release work should change `versionName`.

### versionCode

- `versionCode` must increase on every release that reaches `main`.
- Never reuse or decrease a `versionCode`.

### SemVer Decision Rules

- `MAJOR`: incompatible or user-visible breaking changes
- `MINOR`: backward-compatible features
- `PATCH`: backward-compatible fixes, refactors, docs, tests, or maintenance with no new user-facing capability

## Release Flow

1. Complete the changes on `main` or cherry-pick them onto `main`.
2. Update `app/build.gradle.kts` with the target `versionName` and the next `versionCode`.
3. Run the required validation checks.
4. Push `main`.
5. Create the Git tag `vX.Y.Z` on `main`.

## Daily Workflow

1. Start from an updated `main`.
2. Make the change directly on `main` or on an optional short-lived branch.
3. Commit using Conventional Commits.
4. Run the relevant checks before pushing.
5. Push to `main` when the branch is ready.

## Non-Negotiable Rules For Agents

- Keep `main` releasable.
- Do not expose development suffixes in any user-visible version label, including the About screen.
- Do not change `versionName` or `versionCode` unless the user explicitly requests a release/versioning step.
- Do not push or tag a release if the required checks fail.
