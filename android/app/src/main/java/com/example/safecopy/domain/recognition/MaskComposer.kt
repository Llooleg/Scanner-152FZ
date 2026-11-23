package com.example.safecopy.domain.recognition

enum class NumberMode { FULL, KEEP_LAST_4 }

/** Что пользователь решил скрыть. Действует на все страницы документа. */
data class MaskSettings(val hidden: Set<FieldKind>, val numberMode: NumberMode) {
    companion object {
        /** Как в макете: всё скрыто, у номера видны 4 последние цифры, фото показывается. */
        fun defaults(available: Set<FieldKind>) =
            MaskSettings(hidden = available - FieldKind.PHOTO, numberMode = NumberMode.KEEP_LAST_4)
    }
}

/**
 * Итоговые закрашиваемые области страницы.
 * [numberFallback] — пользователь просил оставить 4 цифры, но где-то их не удалось найти, и там номер скрыт целиком.
 */
data class PageMask(val rects: List<NormRect>, val numberFallback: Boolean)

object MaskComposer {

    fun compose(page: LocatedPage, settings: MaskSettings, manual: List<NormRect>): PageMask {
        val rects = mutableListOf<NormRect>()
        var fallback = false
        for ((kind, field) in page.fields) {
            if (kind !in settings.hidden) continue
            rects += field.areas
            for (number in field.numbers) {
                val keep = settings.numberMode == NumberMode.KEEP_LAST_4 && number.keepable
                val digits = number.lastDigits
                when {
                    keep && digits != null -> rects += number.area.subtract(digits)
                    else -> {
                        if (keep) fallback = true
                        rects += number.area
                    }
                }
            }
        }
        rects += manual
        return PageMask(rects, fallback)
    }
}
