# Contributing

## What We Accept

| Type | Accepted? |
|---|---|
| Bug fixes | Yes |
| Translations | Yes — via Weblate |
| New exchange rate providers | Discuss first |
| New features | Discuss first |
| Refactors | Discuss first |

The project favours simplicity. Large feature additions are unlikely to be merged without prior discussion.

## Translations

Translations are managed on [Weblate](https://translate.codeberg.org/engage/currencies/). Do **not** open PRs that edit string resource XML files directly — translate through the Weblate interface.

## Code Contributions

### Setup

1. Fork and clone the repository.
2. Open in Android Studio (latest stable recommended).
3. JDK 21 is required.
4. `./gradlew assembleFdroidDebug` should build without errors.

### Branch Naming

Use descriptive branch names. The CI `apk-artifact.yaml` workflow runs on any non-master push and uploads a debug APK as an artifact for easy review.

### Code Style

- Kotlin only (no Java in `app/` or `helpers/`).
- Follow existing patterns — MVVM, Repository, LiveData.
- Run `./gradlew detekt` locally before opening a PR. CI will fail on Detekt findings.
- Avoid `java.lang.*` qualifiers (Kotlin imports these automatically).
- Avoid swallowed exceptions: always use the caught exception variable in the catch block.

### Pull Request Checklist

- [ ] `./gradlew check assembleDebug` passes locally
- [ ] No new Detekt warnings
- [ ] If adding a dependency: check F-Droid licence compatibility
- [ ] `mXparser` must remain at 4.4.3 — v5+ is not F-Droid compatible

### Commit Message Convention

```
type(scope): short description

Examples:
feat(providers): add ECB historical fallback
fix(calculator): handle division by zero
chore(deps): bump moshi to 1.15.2
chore(ci): pin checkout action to SHA
```

## Reporting Issues

Open a GitHub Issue with:
- Android version
- App version (`Settings → About`)
- Selected exchange rate provider
- Steps to reproduce
- Expected vs. actual behaviour
