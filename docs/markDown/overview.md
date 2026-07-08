# Currencies — Overview

**Currencies** is a simple, privacy-focused Android currency converter designed as a travel companion rather than a financial trading tool.

- **Package**: `de.salomax.currencies`
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 37 (Android 15)
- **License**: GNU General Public License v3+
- **Language**: Kotlin

## What It Does

Convert between 30–160+ world currencies using live exchange rates fetched from your chosen provider. All conversions happen on-device with no ads, no analytics, and no user tracking.

## Core Features

| Feature | Details |
|---|---|
| Exchange rate providers | 6 active providers (ECB, OER, InforEuro, Bank of Canada, Norges Bank, Bank Rossii) |
| Built-in calculator | Full arithmetic (+, −, ×, ÷) before conversion |
| Fee calculator | Optional configurable foreign-exchange fee percentage |
| Historical rates | Access rates back to 2010 |
| Rate charts | 1-year historical timeline visualization |
| Starred currencies | Favourite/filter currencies for quick access |
| Themes | Light, dark, and pure-black modes; follows system setting |
| Foldable support | Adaptive multi-pane layout via WindowInfoTracker |
| Internationalization | 20+ languages via Weblate |

## Distribution

| Store | Link |
|---|---|
| Google Play | `play.google.com/store/apps/details?id=de.salomax.currencies` |
| F-Droid | `f-droid.org/packages/de.salomax.currencies/` |

The `fdroid` build flavor excludes any Play-Store-specific APIs and is reproducible.

## Privacy

The app requests only the `INTERNET` permission. No analytics SDK, no crash reporter, no advertising ID access. Exchange rates are fetched directly from public central-bank or open-data APIs.

## Version Scheme

Versions follow [Semantic Versioning](https://semver.org/). The Android `versionCode` is derived automatically: `1.23.0 → 12300`.
