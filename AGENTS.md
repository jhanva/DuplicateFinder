# Codex Rules

## Style

- Do not use emojis in source code, comments, documentation, commit messages, or PR descriptions.
- Use plain, professional text only.

## Branching And Release Flow

This repository follows a strict GitFlow-style process.

### Long-Lived Branches

- `main` is the production branch. It must always be releasable.
- `develop` is the integration branch for the next release.

### Protected Branch Rules

- Never push directly to `main`.
- Never push directly to `develop`.
- Every change must arrive through a Pull Request.
- `main` only accepts PRs whose head branch is exactly `develop`.
- `develop` accepts PRs from short-lived work branches and from back-merges after a release or hotfix.
- Force-pushes and branch deletion must be disabled on `main` and `develop`.
- PRs into `main` or `develop` must require at least one approval, resolved conversations, and passing checks.

### Short-Lived Branches

Create all working branches from `develop`, except hotfixes.

- `feature/<short-description>` for new functionality
- `fix/<short-description>` for non-critical bug fixes
- `refactor/<short-description>` for internal improvements without behavior changes
- `docs/<short-description>` for documentation-only work
- `test/<short-description>` for test-only work
- `chore/<short-description>` for maintenance tasks
- `release/<x.y.z>` from `develop` when preparing a release
- `hotfix/<x.y.z>` from `main` for urgent production fixes

Use lowercase kebab-case names.

## Pull Request Policy

- All merges must happen through GitHub Pull Requests.
- Use `squash merge` for all PRs so the final commit history stays clean and the PR title becomes the permanent commit message.
- PR titles must follow Conventional Commits.
- A PR must target the correct base branch:
  - `feature/*`, `fix/*`, `refactor/*`, `docs/*`, `test/*`, `chore/*` -> `develop`
  - `release/*` -> `develop`
  - `hotfix/*` -> `develop`
  - `develop` -> `main`
  - Post-release or post-hotfix sync PR -> `develop` only when explicitly needed to reconcile history
- Do not merge with unresolved review comments.
- Do not merge if required checks fail.
- Do not merge if the branch is behind its base branch and needs to be updated.

Codex may create, review, approve, and merge PRs when all repository rules in this file are satisfied.

## Conventional Commits

Every commit and every PR title must use Conventional Commits.

Format:

`type(scope): short summary`

Examples:

- `feat(scan): add folder prefilter before duplicate scan`
- `fix(trash): prevent restore when source path is missing`
- `refactor(data): simplify MediaStore cursor mapping`
- `docs(gitflow): document release and hotfix process`

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
- `gitflow`
- `release`

Breaking changes must use `!` or a `BREAKING CHANGE:` footer.

Examples:

- `feat(settings)!: remove legacy similarity threshold migration`
- `feat(scan): change grouping algorithm`
- `BREAKING CHANGE: duplicate grouping now requires cached hashes to be rebuilt`

## Versioning Policy

This project uses Semantic Versioning for `versionName` and a monotonically increasing Android `versionCode`.

### versionName

- `versionName` must always use the full `MAJOR.MINOR.PATCH` format.
- Example: `1.0.0`, not `1.0`.
- User-facing versions must stay clean and release-like.
- Do not append `-debug`, `-dev`, `-test`, or similar suffixes to the user-visible `versionName`.
- Debug or internal differentiation must use other mechanisms such as `applicationIdSuffix`, build metadata, branch names, or release notes.
- Only release and hotfix work should change `versionName`.

### versionCode

- `versionCode` must increase on every release that reaches `main`.
- Never reuse or decrease a `versionCode`.
- Feature branches do not change `versionCode` unless they are being converted into a release branch.

### SemVer Decision Rules

- `MAJOR`: incompatible or user-visible breaking changes
- `MINOR`: backward-compatible features
- `PATCH`: backward-compatible fixes, refactors, docs, tests, or maintenance with no new user-facing capability

### Release Ownership

- `develop` contains the next unreleased work.
- `main` reflects the latest released version.
- Each merge into `main` must end in a tag named `vX.Y.Z`.

## Release Flow

### Standard Release

1. Branch `release/x.y.z` from `develop`.
2. On the release branch, allow only stabilization work:
   - bug fixes
   - test fixes
   - documentation updates
   - version bump and release notes
3. Update `app/build.gradle.kts` with the target `versionName` and the next `versionCode`.
4. Open a PR from `release/x.y.z` to `develop`.
5. Merge with `squash merge` after approval and passing checks.
6. Open a PR from `develop` to `main`.
7. Merge the `develop` -> `main` PR after approval and passing checks.
8. Create the Git tag `vX.Y.Z` on `main`.

### Hotfix Release

1. Branch `hotfix/x.y.z` from `main`.
2. Apply the minimum safe fix.
3. Update `app/build.gradle.kts` with the new patch `versionName` and the next `versionCode`.
4. Open a PR from `hotfix/x.y.z` to `develop`.
5. Merge with `squash merge` after approval and passing checks.
6. Open a PR from `develop` to `main`.
7. Merge the `develop` -> `main` PR after approval and passing checks.
8. Create the Git tag `vX.Y.Z` on `main`.

## Daily Workflow

### New Work

1. Start from an updated `develop`.
2. Create a short-lived branch with the correct prefix.
3. Commit using Conventional Commits.
4. Open a PR to `develop`.
5. Squash merge after approval and passing checks.

### Releasing

1. Freeze `develop` into `release/x.y.z`.
2. Stabilize the branch.
3. Merge the release branch back into `develop` by PR.
4. Promote `develop` to `main` by PR only.
5. Tag the release.

### Emergency Fix

1. Branch from `main` into `hotfix/x.y.z`.
2. Fix only the urgent issue.
3. Merge the hotfix branch into `develop` by PR.
4. Promote `develop` to `main` by PR only.
5. Tag the release.

## Non-Negotiable Rules For Agents

- Do not commit directly on `main`.
- Do not commit directly on `develop`.
- Do not merge to `main` without a PR.
- Do not open or merge a PR to `main` from any branch other than `develop`.
- Do not bypass approvals or required checks.
- Do not expose development suffixes in any user-visible version label, including the About screen.
- Do not change `versionName` or `versionCode` outside release or hotfix work unless the user explicitly requests it.
- Do not open a release PR without confirming the target SemVer bump.
- If a PR is squash-merged, ensure the PR title is the final Conventional Commit message that should stay in history.
