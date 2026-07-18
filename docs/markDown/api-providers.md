# Exchange Rate Providers

Currencies supports multiple exchange-rate data sources. The active provider is selected in **Settings → Exchange rate provider**. Switching takes effect on the next refresh.

## Comparison

| Provider | Currencies | Update freq | Base | Notes |
|---|---|---|---|---|
| [Frankfurter.app](https://frankfurter.app/) | ~33 | Business days | EUR | European Central Bank data |
| [OpenExchangerates](https://openexchangerates.org/) | 160+ | Hourly | USD | Free tier requires API key |
| [InforEuro](https://commission.europa.eu/funding-tenders/procedures-guidelines-tenders/information-contractors-and-beneficiaries/exchange-rate-inforeuro_en) | ~150 | Monthly | EUR | EU Commission accounting rates |
| [Bank of Canada](https://www.bankofcanada.ca/rates/exchange/daily-exchange-rates/) | ~23 | Business days | CAD | Canadian Central Bank |
| [Norges Bank](https://www.norges-bank.no/en/topics/Statistics/exchange_rates/) | ~40 | Business days | NOK | Norwegian Central Bank |
| [Bank Rossii](https://cbr.ru/eng/currency_base/daily/) | ~44 | Business days | RUB | Russian Central Bank |
| [Bank of Israel](https://www.boi.org.il/en/economic-roles/statistics/foreign-exchange-market/exchange-rates/) | ~14 | Business days | ILS | Israeli Central Bank |

## Deactivated / Removed Providers

| Provider | Reason |
|---|---|
| fer.ee | Persistent API instability |
| exchangerate.host | API shutdown |

## Historical Rate Support

All active providers support historical rates to varying degrees. Frankfurter.app data goes back to **2010-01-04** (ECB reference start date). Bank Rossii and InforEuro have their own historical archives.

When a historical date is selected in the app, the provider's history endpoint is queried instead of the live-rates endpoint.

## Provider Implementation

Each provider is implemented as an object inside `app/src/main/kotlin/de/salomax/currencies/model/provider/`. They all conform to the `Api` abstract interface defined in `ApiProvider.kt`:

```kotlin
abstract fun getRates(base: Currency, date: LocalDate?): Call<ExchangeRates?>
abstract fun getTimeline(base: Currency, quote: Currency): Call<Timeline?>
```

Response parsing is handled by provider-specific Moshi adapters (`model/adapter/`) or SAX XML parsers (for Norges Bank and Bank Rossii, which return XML).
