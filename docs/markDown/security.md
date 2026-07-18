# Security

## Supply Chain

### Actions Pinning

Every GitHub Actions `uses:` reference is pinned to a full 40-character commit SHA, with the human-readable tag preserved in a trailing comment:

```yaml
uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7
```

This prevents a compromised or reused tag from injecting malicious code into CI. Enforced by the Semgrep rule `yaml.github-actions.security.github-actions-mutable-action-tag` and verified by the OpenSSF Scorecard `Pinned-Dependencies` check.

### Dependabot Cooldown

Both Gradle and GitHub Actions ecosystems enforce a **7-day cooldown** (`cooldown: default-days: 7`) before Dependabot proposes a version bump. This mitigates supply-chain attacks where a dependency is compromised shortly after publication.

### Dependency Review

The `dependency-review` workflow blocks any PR that introduces a dependency with a **high or critical** CVE (CVSS ≥ 7).

### OWASP Dependency Check

Weekly scan of the runtime classpath against the NVD database. Fails the workflow on CVSS ≥ 7 findings. Also flags retired/archived libraries.

## Secrets

- Release signing credentials are stored in `secrets.properties` (gitignored) and referenced only at build time.
- No API keys are stored in source code. OpenExchangerates key (if used) is expected as an environment variable or build config field.
- Gitleaks scans the full repository weekly for accidental credential commits.

## User-initiated Backup

Users can export their settings from **Settings → Backup & Restore** to a location of their choosing via the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`). The backup file contains three SharedPreferences namespaces (`prefs`, `last_state`, `starred_currencies`) as versioned JSON; the cached exchange-rate table is deliberately excluded.

The app requests **no storage permission** — the SAF picker returns a scoped `content://` URI that the user has explicitly granted for that single file.

### Optional password encryption

At export time the user may tick **Encrypt with a password**. When set:

- Password → 256-bit AES key via **Argon2id v1.3** (`m=32 MiB, t=3, p=1`) with a random 32-byte salt. Argon2id is memory-hard, which neutralises the √N speedup a quantum attacker gets from Grover's algorithm — parallelism gains don't help when memory bandwidth dominates.
- The `namespaces` block is encrypted with **AES-256-GCM** using a random 12-byte IV and 128-bit auth tag. AES-256 is itself quantum-resistant: Grover reduces effective security to ~128 bits, which remains out of reach.
- KDF, cipher, and Argon2 cost parameters (`memoryKib`, `iterations`, `parallelism`) are recorded in the wrapper JSON so future readers can reject unknown algorithms instead of silently mis-decrypting.
- Passwords are held as `CharArray` and zeroed by both UI and manager after use; UTF-8 encoding for Argon2 goes through NIO to avoid a `String` allocation that would linger in the pool.

On import the app detects the `encryption` block, prompts for the password, and re-prompts on GCM tag failure. Plaintext files still import unchanged; an earlier revision that used PBKDF2-HMAC-SHA256 (210 000 iterations) is still readable via a fallback branch keyed on the file's `kdf` field.

### Scheduled backup

The user may opt in to periodic backups from **Settings → Backup & Restore → Scheduled backup**. Frequency is a single-choice list (Off / Daily / Weekly / Monthly) driven by WorkManager `PeriodicWorkRequest`. The destination is a directory chosen via `ACTION_OPEN_DOCUMENT_TREE`; the read/write grant is made persistable so it survives reboots.

- Scheduled exports are always **plaintext**. Prompting the user for a password at midnight defeats the point of a scheduled job, and stashing the password in preferences would defeat the point of the password. Users who require encryption at rest use the manual export flow.
- Files are named `currencies-backup-YYYYMMDD-HHmmss.json` so lexicographic sort matches chronological order; the worker keeps the most recent N (default 5) and deletes older ones, matching only files with the `currencies-backup-` prefix so nothing else in the folder is touched.
- WorkManager persists jobs across reboots, but the app also calls `BackupScheduler.reschedule` on `Application.onCreate` to recover from app-update or force-stop edge cases where jobs are dropped.

Automatic Android backup remains disabled (`android:allowBackup="false"`) — user-initiated export is the only supported path off-device.

## Runtime Permissions

The app declares a single permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

No location, contacts, storage, camera, or microphone access.

## Network Security

`res/xml/network_security_config.xml` refuses cleartext HTTP globally. Every configured exchange-rate provider serves HTTPS only, so the config closes off downgrade / MITM avenues without breaking real traffic.

## Backups

`android:allowBackup="false"` and `android:fullBackupContent="false"` are set in the manifest, backed by an explicit `res/xml/data_extraction_rules.xml` that excludes every domain (`root`, `database`, `sharedpref`, `external`, `file`) from both **cloud backups** and **device-to-device transfers** on Android 12+.

Rationale: SharedPreferences contain the user's OpenExchangeRates API key (if any), their fee config, and starred currencies — none of which should leak into an off-device Google backup by default. Users who want backups get an in-app, opt-in mechanism (see the Backup feature in Settings) with optional password encryption.

## Code Analysis

| Tool | Scope | Frequency |
|---|---|---|
| Detekt | Kotlin source (app + helpers) | Every PR and master push |
| Qodana JVM Community | Kotlin / Java | Every PR, master push, weekly |
| CodeQL | GitHub Actions YAML | Every PR, master push, weekly |
| Semgrep | Security patterns | Every PR and master push |

## OpenSSF Scorecard

The repository is evaluated weekly by the [OpenSSF Scorecard](https://securityscorecards.dev/). Results are uploaded to the GitHub Security tab. Key checks:

- **Pinned-Dependencies** — all CI actions and Docker images use SHA pins
- **Branch-Protection** — master branch is protected
- **SAST** — multiple static analysis tools are active
- **Dependency-Update-Tool** — Dependabot is configured
- **Token-Permissions** — workflows use least-privilege permissions
