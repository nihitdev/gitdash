# GitDash

![GitDash version](https://img.shields.io/badge/version-0.1.0-2563eb?style=flat-square)
![Java](https://img.shields.io/badge/Java-26-e76f00?style=flat-square&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303a?style=flat-square&logo=gradle&logoColor=white)
![Platform](https://img.shields.io/badge/platform-Linux-fcc624?style=flat-square&logo=linux&logoColor=black)
![Safety](https://img.shields.io/badge/default-read--only-16a34a?style=flat-square)
[![CI](https://github.com/nihitdev/gitdash/actions/workflows/ci.yml/badge.svg)](https://github.com/nihitdev/gitdash/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/nihitdev/gitdash?style=flat-square)](https://github.com/nihitdev/gitdash/releases/latest)
[![License](https://img.shields.io/github/license/nihitdev/gitdash?style=flat-square)](LICENSE)

GitDash is a fast, read-mostly terminal dashboard for developers who maintain many local Git repositories. It discovers repositories once, stores a registry in the appropriate XDG state directory, then inspects every registered working tree concurrently using the installed `git` executable.

```text
$ gitdash status
REPOSITORY  BRANCH              STATE       AHEAD  BEHIND   LAST COMMIT
gitdash     main                clean           0       0         5m ago
website     develop             dirty           2       0         2h ago

2 repositories
1 require attention
```

## Requirements and installation

- Linux or another Unix-like environment
- Windows 10/11 with PowerShell 5.1 or newer
- Eclipse Temurin/Adoptium JDK 26 (another conforming Java 26 JDK should also work)
- Git available on `PATH`

No preview Java features are used. Build and install a distribution with:

```bash
./gradlew build
./gradlew installDist
build/install/gitdash/bin/gitdash --version
```

For a normal `gitdash` command, run `./install.sh`. The default prefix is `/usr/local`. The installer writes directly when permitted, detects root, and only invokes `sudo` when elevation is necessary and available. A user installation needs no elevation:

```bash
PREFIX="$HOME/.local" ./install.sh
```

Install the latest release without cloning the repository:

```bash
curl -fsSLO https://github.com/nihitdev/gitdash/releases/latest/download/install.sh
PREFIX="$HOME/.local" sh install.sh
```

Ensure the selected prefix's `bin` directory is on `PATH`.

### Windows

From PowerShell, install the latest release for the current user:

```powershell
Invoke-WebRequest https://github.com/nihitdev/gitdash/releases/latest/download/install.ps1 -OutFile install.ps1
.\install.ps1
gitdash --version
```

The default location is `%LOCALAPPDATA%\Programs\GitDash`. The installer adds its `bin` directory to the user PATH; open a new terminal afterward. Use `-Prefix C:\Tools\GitDash` to select another location or `-NoPath` to leave PATH unchanged. Administrator privileges are not required for the default location.

## First use

```bash
gitdash scan ~/Projects
gitdash scan /mnt/storage/Projects --max-depth 8 \
  --exclude node_modules --exclude target --exclude build
gitdash status
gitdash summary
```

GitDash recognizes both `.git` directories and worktree-style `.git` files. It does not follow symlinks by default, skips inaccessible paths with warnings, and stops descending when it finds a repository. `scan --nested` explicitly enables nested repository discovery. Multiple scans merge into one persistent, path-deduplicated registry.

## Commands

| Command | Purpose |
| --- | --- |
| `scan PATH` | Discover and register repositories |
| `status` | Show the dashboard |
| `summary` | Show aggregate health counts |
| `dirty`, `clean`, `ahead`, `behind`, `conflicts` | Focused status views |
| `stale [--days N]` | Show repositories with old last commits |
| `repos [--search TEXT]` | Search the registry |
| `remove REPO` | Unregister one repository without touching its files |
| `prune [--dry-run]` | Find or unregister repository paths that no longer exist |
| `show REPO` | Detailed working-tree, remote, and commit information |
| `doctor` | Diagnose working-tree and synchronization issues |
| `fetch REPO`, `fetch --all`, `fetch --group NAME` | Concurrently update remote metadata |
| `config`, `config alias NAME PATH` | Inspect configuration or set an alias |
| `cache clear` | Clear the cache directory (status itself is never cached) |
| `completion bash\|zsh\|fish` | Generate shell completion |
| `benchmark` | Compare temporary-repository sequential/concurrent inspection |

Global options include `--help`, `--version`, `--debug`, and `--no-color`. GitDash currently emits no ANSI styling when output is redirected; `NO_COLOR` is therefore naturally respected.

### Filtering and sorting

Status filters compose:

```bash
gitdash status --dirty --branch main
gitdash status --behind --remote github.com
gitdash status --group native
gitdash status --detached
gitdash status --no-upstream --limit 20
gitdash status --stale --stale-days 90
```

Additional filters include `--no-remote` and `--invalid`. `repos --missing` lists missing registered paths, while `repos --json` provides a machine-readable registry view.

Sort with `--sort name|path|status|branch|commit|ahead|behind`; add `--reverse` to reverse the complete deterministic order. The path is always a tie-breaker.

### Stale repositories

A repository is stale when it has at least one commit and its most recent commit timestamp is older than the configured threshold (30 days by default). Empty repositories are not classified as stale. Override the threshold with `gitdash stale --days 90`.

### Doctor and exit codes

`doctor` reports modifications, staged/untracked files, conflicts, detached HEAD, missing upstream/remotes, ahead/behind/diverged branches, stale repositories, missing paths, and invalid repositories. It exits `0` when healthy and `2` when attention is required. Fetch exits `3` if any selected fetch fails. Ordinary command/usage errors are nonzero.

### Fetch safety

Fetch runs `git fetch --prune` independently and concurrently. A failure does not cancel other repositories. It never merges, pulls, rebases, checks out, resets, cleans, commits, or pushes. `status` never performs a network operation.

## Machine-readable output

`gitdash status --json` writes a JSON object containing `schemaVersion` (currently `1`), `generatedAt`, and a `repositories` array. Each row includes identity, branch/detached state, file counts, upstream/ahead/behind/diverged state, sanitized remote details, last-commit metadata, and an optional inspection error.

```bash
gitdash status --json | jq '.repositories[] | select(.state != "clean")'
```

`gitdash status --porcelain` emits one tab-separated row per repository:

```text
PATH<TAB>BRANCH<TAB>STATE<TAB>AHEAD<TAB>BEHIND<TAB>UPSTREAM
```

Backslashes, tabs, and newlines in textual fields are escaped. Diagnostics go to stderr and never contaminate machine output.

## Configuration and XDG paths

Configuration is optional. GitDash validates `$XDG_CONFIG_HOME/gitdash/config.toml`, falling back to `~/.config/gitdash/config.toml`:

```toml
[scan]
max_depth = 8
follow_symlinks = false
exclude = ["node_modules", "target", "build", ".cache"]

[status]
stale_days = 30

[ui]
color = "auto"
unicode = true

[concurrency]
enabled = true
max_parallel = 32

[aliases]
zari = "/mnt/storage/Projects/zari"

[groups]
native = ["syswatch", "zari"]
```

State is stored in `$XDG_STATE_HOME/gitdash/` (fallback `~/.local/state/gitdash/`), and cache in `$XDG_CACHE_HOME/gitdash/` (fallback `~/.cache/gitdash/`). A malformed file produces a direct diagnostic and no silent fallback.

## Architecture and security

The CLI uses picocli. Immutable records model repositories/status, Jackson renders JSON and persists an atomically replaced registry, and tomlj parses configuration. A centralized `GitClient` uses `ProcessBuilder` argument lists—never shell command concatenation—sets `GIT_TERMINAL_PROMPT=0`, captures both streams and duration, enforces timeouts, terminates timed-out children, and preserves interruption. HTTP credentials and common token query parameters are redacted before display.

Repository inspection uses Git porcelain v2 and NUL-delimited/log machine formats. Virtual threads handle independent repositories, bounded by `max_parallel`; results are collected in registry order and sorted deterministically. A corrupt repository becomes an `invalid` row instead of failing the dashboard.

## Troubleshooting

- **No repositories:** run `gitdash scan PATH`, then inspect `gitdash repos`.
- **Git timed out:** check credentials/network for `fetch`; status uses only local operations.
- **Ambiguous name:** use the absolute registered path or create an alias.
- **Java mismatch:** `java -version` must report 26; Gradle also requests a Java 26 toolchain.
- **Malformed config:** correct the path and parse location shown in the error.

## Roadmap

Potential later work includes richer terminal-width adaptation/colors, dynamic repository completion, registry removal/rename commands, and benchmark-driven metadata caching. Working-tree status will not be cached unless freshness can be made explicit and correct. Mutating Git operations such as pull, merge, rebase, checkout, reset, clean, commit, and push are intentionally outside v0.1.

## License

GitDash is licensed under the [Apache License 2.0](LICENSE).
