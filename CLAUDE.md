# Repo instructions for Claude Code

Read the project docs before making changes. Contributor rules — including branching, code style, PR checklist, and commit conventions — live in [`docs/markDown/contributing.md`](docs/markDown/contributing.md).

Other reference docs are in [`docs/markDown/`](docs/markDown/):

- [`overview.md`](docs/markDown/overview.md) — project overview
- [`architecture.md`](docs/markDown/architecture.md) — architecture
- [`build-and-flavors.md`](docs/markDown/build-and-flavors.md) — build variants
- [`api-providers.md`](docs/markDown/api-providers.md) — exchange rate providers
- [`ci-cd.md`](docs/markDown/ci-cd.md) — CI/CD pipelines
- [`security.md`](docs/markDown/security.md) — security posture

Follow the guidance in those files. If a docs update is needed, edit the relevant `.md` there.

## Code-shape defaults

When touching code, always look for and apply these where possible:

- **Extract functions** for any block that's non-trivial or reused — even once, if it clarifies intent.
- **Deduplicate**: identical or near-identical logic in two places should become one helper (top-level `internal` fun, extension, or shared util in `model/adapter/AdapterUtils.kt` / similar).
- **Hoist to variables/constants**: repeated expressions become locals; repeated literals (URLs, date patterns, magic numbers, keys) become named `const val` or `val` at file/package scope.

Do this in-line with whatever task you're doing — don't gate it behind a separate "refactor" ask.
