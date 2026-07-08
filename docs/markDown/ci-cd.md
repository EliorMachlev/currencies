# CI / CD

All automation lives in `.github/workflows/`. Every workflow pins its GitHub Actions to a full 40-character commit SHA to prevent supply-chain attacks.

## Workflow Summary

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yaml` | Push → `master` | Lint, test, build debug APK |
| `apk-artifact.yaml` | Push → non-master, manual | Build fdroid debug APK and upload as artifact |
| `detekt.yaml` | PR, push → `master` | Kotlin static analysis |
| `qodana.yaml` | PR, push → `master`, weekly | JetBrains Qodana JVM analysis |
| `codeql.yaml` | PR, push → `master`, weekly | GitHub CodeQL (Actions YAML) |
| `semgrep.yaml` | PR, push → `master` | SAST security pattern scanning |
| `gitleaks.yaml` | Weekly (Mon 07:00 UTC) | Secret / credential scanning |
| `owasp-dependency-check.yaml` | Weekly (Mon 08:00 UTC) | Dependency vulnerability scan (CVSS ≥ 7) |
| `dependency-review.yaml` | PR | Block high-severity new dependencies |
| `scorecard.yaml` | Push → `master`, weekly | OpenSSF Scorecard supply-chain score |
| `actionlint.yaml` | PR/push on `.github/workflows/**` | Validate workflow YAML syntax |

## Build Gate (`build.yaml`)

Runs `./gradlew check assembleDebug`, which includes:
- `lint` — Android Lint (missing translations suppressed)
- `test` — JUnit unit tests
- `assembleDebug` — compiles the debug APK for all flavors

## Security Scans

### Detekt
- Version: 1.23.7 (pinned via `DETEKT_VERSION` env var)
- Inputs: `app/src`, `helpers/src`
- JVM target: 21
- Output: SARIF uploaded to GitHub Security tab + artifact retained 14 days

### Qodana
- Image: `qodana-jvm-community:2025.1`
- Posts inline PR comments on findings
- SARIF uploaded to GitHub Security tab

### CodeQL
- Language scope: `actions` (YAML workflows only — Kotlin delegated to Qodana)

### OWASP Dependency Check
- CVSS threshold: 7 (high+)
- Scans runtime classpath only
- Detects retired/archived dependencies
- Uploads HTML + XML reports as artifact

### OpenSSF Scorecard
- Evaluates pinned-dependencies, branch-protection, SAST, etc.
- Results written to `scorecard-results.sarif` and uploaded to Security tab

## Dependabot

Configured in `.github/dependabot.yml` with weekly Monday schedule.

**Ecosystems:**

| Ecosystem | Directories | Open PR limit | Cooldown |
|---|---|---|---|
| `gradle` | `/`, `/app`, `/helpers` | 10 | 7 days |
| `github-actions` | `/` | 5 | 7 days |

**Grouped updates** keep related libraries in one PR:
- `androidx.*`
- `com.google.android.material:*`
- `org.jetbrains.kotlin*` + `com.google.devtools.ksp*`
- `com.squareup.moshi:*`
- `com.github.kittinunf.fuel:*`
- Test dependencies
- All GitHub Actions (single PR)

**Pinned dependency:** `MathParser.org-mXparser` is ignored at v5+ due to licence incompatibility with F-Droid.

## Permission Model

All workflows use `permissions: contents: read` by default. Additional permissions are granted only when needed:

| Workflow | Extra permissions |
|---|---|
| `detekt.yaml` | `security-events: write` |
| `codeql.yaml` | `security-events: write`, `actions: read` |
| `scorecard.yaml` | `security-events: write`, `id-token: write` |
| `dependency-review.yaml` | `pull-requests: write` |
| `owasp-dependency-check.yaml` | `security-events: write` |
