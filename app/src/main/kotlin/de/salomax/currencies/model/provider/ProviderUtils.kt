package de.salomax.currencies.model.provider

import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.adapter.LocalDateAdapter

// FOK (Faroese króna) is pegged 1:1 to DKK and not listed by upstream APIs —
// query DKK instead and let callers relabel the result on the way back.
internal fun Currency.apiCodeOrDkkForFok(): String =
    if (this == Currency.FOK) "DKK" else this.iso4217Alpha()

// One shared KotlinJsonAdapterFactory across every provider. The factory
// keeps its own class-to-adapter cache internally, so a single instance
// means the reflection scan runs once app-wide per data class instead of
// once per HTTP request. Safe to share: the factory is stateless.
internal val SHARED_KOTLIN_JSON_ADAPTER_FACTORY: KotlinJsonAdapterFactory =
    KotlinJsonAdapterFactory()

// Stateless date adapter — hoisted for the same reason as the factory above.
internal val SHARED_LOCAL_DATE_ADAPTER: LocalDateAdapter = LocalDateAdapter()
