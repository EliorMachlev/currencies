package de.salomax.currencies.model.provider

import de.salomax.currencies.model.Currency

// FOK (Faroese króna) is pegged 1:1 to DKK and not listed by upstream APIs —
// query DKK instead and let callers relabel the result on the way back.
internal fun Currency.apiCodeOrDkkForFok(): String =
    if (this == Currency.FOK) "DKK" else this.iso4217Alpha()
