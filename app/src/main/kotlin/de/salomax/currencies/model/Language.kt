package de.salomax.currencies.model

import android.content.Context
import de.salomax.currencies.R

enum class Language(
    val iso: String,
    private val nameNative: String?,
    private val nameLocalized: Int
) {
    SYSTEM("system", null, R.string.system_default),
    IN("in", "Bahasa Indonesia", R.string.language_in),
    CA("ca", "Català", R.string.language_ca),
    CS("cs", "Čeština", R.string.language_cs),
    DA("da", "Dansk", R.string.language_da),
    DE("de", "Deutsch", R.string.language_de),
    ET("et", "Eesti", R.string.language_et),
    EN("en", "English", R.string.language_en),
    EN_GB("en_GB", "English (United Kingdom)", R.string.language_en_GB),
    EO("eo", "Esperanto", R.string.language_eo),
    ES("es", "Español", R.string.language_es),
    FR("fr", "Français", R.string.language_fr),
    HR("hr", "Hrvatski", R.string.language_hr),
    IS("is", "Íslenska", R.string.language_is),
    IT("it", "Italiano", R.string.language_it),
    HU("hu", "Magyar", R.string.language_hu),
    NL("nl", "Nederlands", R.string.language_nl),
    NB("nb", "Norsk", R.string.language_nb),
    PL("pl", "Polski", R.string.language_pl),
    PT_BR("pt_BR", "Português (Brasil)", R.string.language_pt_BR),
    SV("sv", "Svenska", R.string.language_sv),
    VI("vi", "Tiếng Việt", R.string.language_vi),
    TR("tr", "Türkçe", R.string.language_tr),
    EL("el", "Ελληνικά", R.string.language_el),
    BG("bg", "Български", R.string.language_bg),
    RU("ru", "Русский", R.string.language_ru),
    UK("uk", "Українська", R.string.language_uk),
    IW("iw", "עִבְרִית", R.string.language_iw),
    AR("ar", "اَلْعَرَبِيَّة", R.string.language_ar),
    FA("fa", "فارسی", R.string.language_fa),
    BN("bn", "বাংলা", R.string.language_bn),
    ZH_CN("zh_CN", "简体中文", R.string.language_zh_CN),
    ZH_TW("zh_TW", "正體中文", R.string.language_zh_TW);

    companion object {
        private val isoMapping: Map<String, Language> = entries.associateBy(Language::iso)

        // Android's per-app locale storage normalises legacy ISO 639-1 codes via
        // Locale.toLanguageTag() (iw -> he, in -> id, ji -> yi). Our enum keeps the
        // legacy codes to match the res/values-* folder names, so map modern codes
        // back before lookup.
        private val legacyLanguageAliases = mapOf(
            "he" to "iw",
            "id" to "in",
            "yi" to "ji",
        )

        private fun canonicalize(isoValue: String?): String? {
            if (isoValue == null) return null
            val (lang, rest) = isoValue.split('_', limit = 2)
                .let { it[0] to it.getOrNull(1) }
            val canonicalLang = legacyLanguageAliases[lang] ?: lang
            return if (rest != null) "${canonicalLang}_$rest" else canonicalLang
        }

        fun byIso(isoValue: String?): Language? {
            val canonical = canonicalize(isoValue)
            return isoMapping[canonical]
            // either the resource string has no country, or the given locale has none:
            // use only language without country
                ?: isoMapping.mapKeys { it.key.substringBefore("_") }[canonical?.substringBefore("_")]
        }
    }

    fun nativeName(context: Context): String = when (this) {
        SYSTEM -> context.getString(R.string.system_default)
        else -> this.nameNative as String
    }

    fun localizedName(context: Context): String =
        this.nameLocalized.let { context.getString(it) }

}
