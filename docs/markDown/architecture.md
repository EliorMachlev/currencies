# Architecture

Currencies follows **MVVM** (Model-View-ViewModel) with a Repository layer, implemented in Kotlin with AndroidX Lifecycle components.

## Layer Overview

```
┌──────────────────────────────────────┐
│              View Layer              │
│  Activities · Custom Views · Dialogs │
└──────────────┬───────────────────────┘
               │ observes LiveData
┌──────────────▼───────────────────────┐
│           ViewModel Layer            │
│  MainViewModel · TimelineViewModel   │
│  PreferenceViewModel                 │
└──────────────┬───────────────────────┘
               │ calls
┌──────────────▼───────────────────────┐
│          Repository Layer            │
│  ExchangeRatesRepository             │
│  ExchangeRatesService                │
└──────────────┬───────────────────────┘
               │ reads/writes
┌──────────────▼───────────────────────┐
│      Data / Persistence Layer        │
│  ApiProvider (HTTP clients)          │
│  Database (SharedPreferences)        │
└──────────────────────────────────────┘
```

## Source Layout

```
app/src/main/kotlin/de/salomax/currencies/
├── model/
│   ├── ApiProvider.kt          # Enum of 6 providers + abstract Api interface
│   ├── Currency.kt             # 190+ ISO-4217 currencies with symbols & flags
│   ├── ExchangeRates.kt        # Snapshot of rates for a base currency
│   ├── Rate.kt                 # Single (currency, rate) pair
│   ├── Timeline.kt             # Historical rate series
│   ├── adapter/                # Moshi / XML adapters per provider
│   └── provider/               # HTTP implementations per provider
├── repository/
│   ├── Database.kt             # Typed SharedPreferences wrapper
│   ├── ExchangeRatesRepository.kt
│   └── ExchangeRatesService.kt # Singleton, coroutine-based fetch orchestration
├── view/
│   ├── main/                   # Converter screen
│   ├── preference/             # Settings screen
│   ├── timeline/               # Chart screen
│   └── BaseActivity.kt
├── viewmodel/
│   ├── main/MainViewModel.kt   # 631 lines — core conversion + calculator logic
│   ├── preference/
│   └── timeline/
└── util/                       # Date, math, text, LiveData helpers

helpers/src/main/kotlin/de/salomax/helpers/
├── changelog/
│   ├── FastlaneToResource.kt   # Fastlane changelogs → Android XML resources
│   └── ResourceToFastlane.kt   # Reverse: Android XML → Fastlane format
└── currencies/
    └── CurrencyFetcher.kt      # Generates localized currency-name XML resources
```

## Key Design Decisions

### Multiple API Providers via Enum + Abstract Interface

`ApiProvider` is an enum whose entries each implement `Api`, an abstract interface exposing `getRates()` and `getTimeline()`. Switching provider at runtime is a single SharedPreferences write; no factory classes required.

### SharedPreferences as the Only Persistence Layer

The app has no SQLite database. All data (cached rates, starred currencies, user state, preferences) lives in namespaced SharedPreferences instances managed by `Database.kt`. This keeps the install size small and the data model simple.

### Moshi + Custom Adapters for Diverse API Formats

Each exchange-rate API returns a different JSON (or XML) schema. Rather than normalising at the network layer, each provider ships its own Moshi adapter (or SAX parser for Norges Bank / Bank Rossii) that maps the raw response to the shared `ExchangeRates` / `Timeline` model.

### LiveData for Reactive UI

ViewModels expose `LiveData<T>` streams. Activities observe them without holding references to the ViewModel, ensuring lifecycle-safety and no memory leaks. `SharedPreferenceLiveData` bridges SharedPreferences changes into the LiveData graph so preference changes propagate automatically.

### Build Flavors: `play` vs `fdroid`

| Dimension | `fdroid` | `play` |
|---|---|---|
| Play Services | None | Allowed |
| Reproducibility | Yes | No |
| Distribution | F-Droid | Google Play |

Source sets under `app/src/fdroid/` and `app/src/play/` override or add flavor-specific code without touching the shared `main` source set.
