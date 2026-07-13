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

### Timeline chart auto-scales decimal places (ignores user preference)

The timeline screen's rate labels (min/max/avg/current/past) do **not** honor the global `decimal_places` preference from Settings. `TimelineViewModel.getDecimalPlaces()` derives the number of decimals from the visible data range: `(max − min).abs().getSignificantDecimalPlaces(3)`, capped at 7.

Why: the user preference (default 2) is tuned for the converter screen where amounts are typed by hand. On the chart, a low-volatility pair like EUR↔USD moves in the 3rd–4th decimal, so a fixed 2-decimal display would render the min/max/avg identical and the chart's whole point would be lost. Auto-scaling keeps enough precision to show variation regardless of the pair.

Trade-off: users who explicitly raise or lower `decimal_places` in Settings will see that setting silently overridden on the chart. Intentional, but surprising — recorded here so future work doesn't "fix" it without weighing the readability cost.

### Timeline chart engine: Vico via Compose interop

The timeline chart is rendered by [Vico](https://github.com/patrykandpatrick/vico) (`com.patrykandpatrick.vico:compose`), hosted inside a `ComposeView` embedded in the otherwise View-based XML layout (`timeline_chart.xml`). `TimelineChart.kt` is a `@Composable` that observes the ViewModel's `LiveData` streams via `observeAsState()` (bridged by `androidx.compose.runtime:runtime-livedata`) and drives a `CartesianChartHost` backed by a `CartesianChartModelProducer`.

Why Vico over the previous engine (SparkView): SparkView is unmaintained and required a hand-rolled adapter, a manual dashed baseline `Paint`, and custom scrub handling. Vico ships all of that as first-class API (`HorizontalLine` decorations, `CartesianMarkerVisibilityListener`) and is actively developed. Cost: introduces Jetpack Compose to a View-only app (~2 MB APK growth from the Compose runtime + Vico).

Behavior preserved: dashed reference line at the last value, scrub-to-past-date via marker-shown callback, theme-aware colors resolved through `MaterialColors.getColor` and passed into the composable.

### Graph options: user-tunable chart chrome

Four `SharedPreferenceLiveData<Boolean>` streams — grid, X-axis labels, Y-axis labels, and highlight-extremes — flow from `Database` through the `TimelineActivity` into `TimelineChart`. All default to `true` so first-run appearance is unchanged. Inside the composable each toggle swaps a Vico component for `null` (e.g. `guideline = if (showGrid) rememberAxisGuidelineComponent() else null`); Vico treats `null` as "don't draw," so no branching in the layer definitions is needed.

### Build Flavors: `play` vs `fdroid`

| Dimension | `fdroid` | `play` |
|---|---|---|
| Play Services | None | Allowed |
| Reproducibility | Yes | No |
| Distribution | F-Droid | Google Play |

Source sets under `app/src/fdroid/` and `app/src/play/` override or add flavor-specific code without touching the shared `main` source set.
