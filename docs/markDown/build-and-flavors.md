# Build & Flavors

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Android Gradle Plugin | see `build.gradle.kts` |
| Kotlin | 2.4.0 |
| Min SDK | 26 |
| Target SDK | 37 |

## Product Flavors

Two flavors are defined in `app/build.gradle.kts`:

| Flavor | Description |
|---|---|
| `fdroid` | F-Droid distribution — no Play Services dependency, fully open-source, reproducible |
| `play` | Google Play distribution — may use Play-specific APIs |

### Common Gradle tasks

```bash
# Debug builds
./gradlew assembleFdroidDebug
./gradlew assemblePlayDebug

# Release builds (requires signing config in secrets.properties)
./gradlew assembleFdroidRelease
./gradlew assemblePlayRelease

# Lint + unit tests + debug build (CI gate)
./gradlew check assembleDebug
```

## Key Dependencies

### Android / AndroidX

| Dependency | Version |
|---|---|
| `androidx.appcompat:appcompat` | 1.7.1 |
| `androidx.lifecycle:lifecycle-*` | 2.11.0 |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 |
| `com.google.android.material:material` | 1.14.0 |
| `androidx.window:window` | 1.5.1 |

### HTTP & Serialisation

| Dependency | Version |
|---|---|
| `com.github.kittinunf.fuel:*` | 2.3.1 |
| `com.squareup.moshi:moshi-kotlin` | 1.15.2 |
| `com.google.devtools.ksp:*` | 2.3.9 |

### Calculator

| Dependency | Version | Note |
|---|---|---|
| `org.mariuszgromada.math:MathParser.org-mXparser` | **4.4.3** | Pinned — v5+ has an F-Droid-incompatible licence |

### Charts

| Dependency | Version |
|---|---|
| `com.robinhood.spark:spark` | 1.2.0 |

### Testing

| Dependency | Version |
|---|---|
| `junit:junit` | 4.13.2 |
| `org.mockito:mockito-core` | 5.23.0 |

## Signing (Release)

Create `secrets.properties` in the project root (not committed):

```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Version Code Generation

`versionCode` is derived automatically from `versionName`:

```
1.23.0  →  1 * 10000 + 23 * 100 + 0  =  12300
```

A pre-build consistency check verifies that `versionName` and `versionCode` are in sync.

## Known Issues

### Gradle deprecation: "Project object as dependency notation" (AGP bug)

**Symptom:** During configuration of `:app`, Gradle prints:

```
Using a Project object as a dependency notation has been deprecated.
This will fail with an error in Gradle 10.
```

**Root cause:** This originates entirely inside **AGP 9.2.1** (`VariantDependenciesBuilder.java:279/333` → `VariantManager.createTestComponents`), not from any build script in this repository. The stack trace confirms no call site in our code.

**Status:** Upstream AGP bug. Will resolve automatically when AGP is upgraded to a version that uses `project(String)` internally.
