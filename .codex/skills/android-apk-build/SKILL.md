---
name: android-apk-build
description: Deterministic Android APK compilation for Gradle projects on Windows/PowerShell. Use when Codex needs to build, rebuild, or verify an APK from a repo with `gradlew.bat`, especially when `JAVA_HOME` may be broken, when the user asks to compile `:app:assembleDebug`, or when the final APK must be confirmed by path, size, and timestamp.
---

# Android Apk Build

Use `scripts/build_apk.ps1` as the default path. Keep the flow fixed unless the user explicitly asks to change it.

## Standard Flow

1. Run `scripts/build_apk.ps1`.
2. Let the script resolve a valid full JDK with both `java.exe` and `jlink.exe`.
3. Prefer Java 17 if present. Otherwise use the first JDK `>= 17`.
4. Run `.\gradlew.bat :app:assembleDebug --console=plain` inside the repo.
5. Verify that `app/build/outputs/apk/debug/app-debug.apk` exists.
6. Report the resolved JDK path, APK path, file size, and last modified time.

## Defaults

- Use the current working directory as the repo root unless `-RepoPath` is provided.
- Use `:app:assembleDebug` unless `-GradleTask` is provided.
- Use `app/build/outputs/apk/debug/app-debug.apk` unless `-ApkRelativePath` is provided.
- Do not mutate global environment variables.
- Do not run `clean` unless the user explicitly asks for a clean build or incremental state is clearly broken.

## Parameters

- `-RepoPath <path>`
- `-GradleTask <task>`
- `-ApkRelativePath <path>`
- `-Clean`
- `-NoDaemon`

## Failure Handling

- If `JAVA_HOME` is invalid, do not stop there. Let the script search common Windows JDK locations and continue.
- If no full JDK is available, stop and report that AGP needs a JDK with `jlink.exe`.
- If Gradle fails, report the failing command and the main error block. Do not claim success.
- If Gradle succeeds but the APK is missing, treat the run as failed and report the expected path.

## DuplicateFinder Defaults

For this repo, the verified default invocation is:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/android-apk-build/scripts/build_apk.ps1 -RepoPath .
```
