package de.salomax.currencies.model.adapter

import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate

// Faroese króna isn't listed by upstream APIs; mirror the Danish krone
// value into the response so FOK still shows up in the UI as a 1:1 peg.
internal fun MutableList<Rate>.addFokFromDkkIfMissing() {
    if (any { it.currency == Currency.FOK }) return
    find { it.currency == Currency.DKK }?.value?.let { dkk ->
        add(Rate(Currency.FOK, dkk))
    }
}
