package de.salomax.currencies.repository

// Top-level JSON keys shared by the whole-app backup ([BackupManager]) and
// the per-cart exporter ([CartExporter]). Both file kinds carry the same
// header so a reader can eyeball provenance and version before parsing the
// type-specific payload; the `type` key (when present) disambiguates the
// payload shape.
internal const val BACKUP_KEY_VERSION = "version"
internal const val BACKUP_KEY_APP = "app"
internal const val BACKUP_KEY_CREATED_AT = "createdAt"
internal const val BACKUP_APP_ID = "de.salomax.currencies"
