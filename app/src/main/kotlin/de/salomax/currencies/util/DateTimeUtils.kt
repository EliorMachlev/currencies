package de.salomax.currencies.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Converts a Unix timestamp to a LocalDate
 */
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()

/**
 * Converts a LocalDate to a Unix timestamp
 */
fun LocalDate.toMillis() = this
    .atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()

/**
 * Returns only the date portion of a combined "date time" pattern (drops anything from the first
 * space onward). Safe to call on a date-only pattern.
 */
fun stripTimePattern(pattern: String): String = pattern.substringBefore(' ').trim()
