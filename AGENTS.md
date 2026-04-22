# Codex Rules

## Shared Codex Skills

- This repository exposes shared Codex skills through `.agents/skills/` and shared custom agents through `.codex/agents/`.
- In this working copy, both directories are linked to the sibling repository `../ai-skills`.
- Treat `../ai-skills` as the source of truth for shared skill content unless the user explicitly asks for a repo-specific override here.
- `.codex/config.toml` is copied locally so this repository can keep its own Codex project settings if needed.
