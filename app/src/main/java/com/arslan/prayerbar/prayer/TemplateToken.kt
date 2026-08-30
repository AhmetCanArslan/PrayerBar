package com.arslan.prayerbar.prayer

import com.arslan.prayerbar.R
import java.util.Locale

/**
 * The placeholders a carrier template can hold. Each has a Turkish and an English spelling; the
 * renderer accepts both, so a template written on a Turkish phone keeps working after a language
 * change — only what the settings UI offers follows the current locale.
 */
enum class TemplateToken(
    val turkish: String,
    val english: String,
    val descriptionRes: Int,
) {
    Prayer("vakit", "prayer", R.string.token_prayer),
    Time("saat", "time", R.string.token_time),
    Remaining("kalan", "remaining", R.string.token_remaining),
    Previous("onceki", "previous", R.string.token_previous),
    Hijri("hicri", "hijri", R.string.token_hijri);

    /** `{vakit}` on a Turkish locale, `{prayer}` elsewhere. */
    fun spelling(locale: Locale = Locale.getDefault()): String =
        "{" + (if (isTurkish(locale)) turkish else english) + "}"

    companion object {
        fun isTurkish(locale: Locale = Locale.getDefault()): Boolean =
            locale.language.equals("tr", ignoreCase = true)

        fun spellingsFor(locale: Locale = Locale.getDefault()): List<String> =
            entries.map { it.spelling(locale) }

        /** Both spellings of one token, for matching a template written under another locale. */
        fun of(name: String): TemplateToken? {
            val lower = name.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.turkish == lower || it.english == lower }
        }
    }
}
