# Contributing to GitDash

Thanks for helping improve GitDash. Changes should preserve its core promise: safe, predictable inspection of many local Git repositories.

## Development setup

You need Git and Eclipse Temurin (or another conforming) JDK 26. GitDash uses the checked-in Gradle wrapper:

```bash
./gradlew clean build
./gradlew installDist
build/install/gitdash/bin/gitdash --version
```

On Windows, use `gradlew.bat` and `build\install\gitdash\bin\gitdash.bat`.

## Making changes

1. Open an issue for significant behavior or interface changes.
2. Keep Git execution centralized in `GitClient`; never concatenate user values into shell commands.
3. Add tests, using temporary repositories rather than personal repositories.
4. Run the complete test suite and `git diff --check`.
5. Keep machine-readable JSON and porcelain output backward compatible.

Do not add working-tree-mutating behavior to inspection commands. Network access must remain explicit, and secrets in remote URLs must be redacted.

## Commit and pull-request guidance

Use focused commits with clear imperative messages. Explain user-visible behavior, tests, platform implications, and safety considerations in the pull request. By contributing, you agree that your contribution is licensed under Apache-2.0.

## Reporting security problems

Do not open a public issue for a vulnerability. Follow [SECURITY.md](SECURITY.md).
