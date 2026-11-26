package com.primebox.ext

import com.lagradost.cloudstream3.MainAPI
import java.util.Locale

/** Language chip helpers: flag emoji + readable language name. */
object Languages {

    private val names = mapOf(
        "en" to ("English" to "\uD83C\uDDFA\uD83C\uDDF8"),
        "hi" to ("Hindi" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "bn" to ("Bengali" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "ta" to ("Tamil" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "te" to ("Telugu" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "ml" to ("Malayalam" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "kn" to ("Kannada" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "mr" to ("Marathi" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "gu" to ("Gujarati" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "pa" to ("Punjabi" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "or" to ("Odia" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "as" to ("Assamese" to "\uD83C\uDDEE\uD83C\uDDF3"),
        "ur" to ("Urdu" to "\uD83C\uDDF5\uD83C\uDDF0"),
        "ne" to ("Nepali" to "\uD83C\uDDF3\uD83C\uDDF5"),
        "si" to ("Sinhala" to "\uD83C\uDDF1\uD83C\uDDF0"),
        "es" to ("Spanish" to "\uD83C\uDDEA\uD83C\uDDF8"),
        "fr" to ("French" to "\uD83C\uDDEB\uD83C\uDDF7"),
        "de" to ("German" to "\uD83C\uDDE9\uD83C\uDDEA"),
        "it" to ("Italian" to "\uD83C\uDDEE\uD83C\uDDF9"),
        "pt" to ("Portuguese" to "\uD83C\uDDE7\uD83C\uDDF7"),
        "ru" to ("Russian" to "\uD83C\uDDF7\uD83C\uDDFA"),
        "ar" to ("Arabic" to "\uD83C\uDDF8\uD83C\uDDE6"),
        "tr" to ("Turkish" to "\uD83C\uDDF9\uD83C\uDDF7"),
        "id" to ("Indonesian" to "\uD83C\uDDEE\uD83C\uDDE9"),
        "ms" to ("Malay" to "\uD83C\uDDF2\uD83C\uDDFE"),
        "th" to ("Thai" to "\uD83C\uDDF9\uD83C\uDDED"),
        "vi" to ("Vietnamese" to "\uD83C\uDDFB\uD83C\uDDF3"),
        "ja" to ("Japanese" to "\uD83C\uDDEF\uD83C\uDDF5"),
        "ko" to ("Korean" to "\uD83C\uDDF0\uD83C\uDDF7"),
        "zh" to ("Chinese" to "\uD83C\uDDE8\uD83C\uDDF3"),
        "tl" to ("Filipino" to "\uD83C\uDDF5\uD83C\uDDED"),
        "fil" to ("Filipino" to "\uD83C\uDDF5\uD83C\uDDED"),
        "multi" to ("Multi" to "\uD83C\uDF10"),
        "all" to ("Multi" to "\uD83C\uDF10")
    )

    private fun base(lang: String?): String =
        lang?.trim()?.lowercase(Locale.US)?.substringBefore('-')?.substringBefore('_').orEmpty()

    fun name(lang: String?): String {
        val key = base(lang)
        names[key]?.let { return it.first }
        if (key.isBlank()) return "Unknown"
        return runCatching {
            Locale(key).getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { it.uppercase(Locale.ENGLISH) }
        }.getOrDefault(key.uppercase(Locale.US))
    }

    fun flag(lang: String?): String {
        val key = base(lang)
        names[key]?.let { return it.second }
        return "\uD83C\uDF10"
    }

    /** e.g. "🇮🇳 Hindi" */
    fun chip(lang: String?): String = "${flag(lang)} ${name(lang)}"

    fun chip(api: MainAPI): String = chip(api.lang)

    fun isIndian(lang: String?): Boolean =
        base(lang) in setOf("hi", "bn", "ta", "te", "ml", "kn", "mr", "gu", "pa", "or", "as", "ur", "sa")
}
