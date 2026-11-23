package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.model.DocumentType

/**
 * Одно вхождение номера документа на странице.
 * [lastDigits] — рамка последних 4 цифр, если её удалось точно найти по символам OCR.
 * [keepable] = false для машиночитаемой зоны: там номер закрашивается всегда.
 */
data class NumberOccurrence(
    val area: NormRect,
    val lastDigits: NormRect?,
    val keepable: Boolean,
    val layout: NumberLayout,
)

data class LocatedField(
    val areas: List<NormRect>,
    val numbers: List<NumberOccurrence> = emptyList(),
)

data class LocatedPage(val role: PageRole, val fields: Map<FieldKind, LocatedField>) {
    fun mapNumbers(transform: (NumberOccurrence) -> NumberOccurrence): LocatedPage {
        val number = fields[FieldKind.NUMBER] ?: return this
        return copy(fields = fields + (FieldKind.NUMBER to number.copy(numbers = number.numbers.map(transform))))
    }
}

/**
 * Поля на страницах: берётся шаблон роли страницы и уточняется по OCR.
 *
 * Уточнение только расширяет области: слово, которое больше чем наполовину лежит в области шаблона,
 * закрашивается целиком, а номера и коды подразделения ищутся по всей странице регулярными выражениями.
 * Сузить область шаблона OCR не может: пропущенное распознаванием слово всё равно останется закрашенным.
 */
object FieldLocator {

    private val PASSPORT_OR_DL_NUMBER = Regex("(?<!\\d)\\d{2} ?\\d{2} ?\\d{6}(?!\\d)")
    private val SNILS_NUMBER = Regex("(?<!\\d)\\d{3}[ -]?\\d{3}[ -]?\\d{3} ?\\d{2}(?!\\d)")
    private val INN_NUMBER = Regex("(?<!\\d)(?:\\d ?){11}\\d(?!\\d)")
    private val DIVISION_CODE = Regex("(?<!\\d)\\d{3} ?- ?\\d{3}(?!\\d)")
    private const val MRZ_MIN_FILLERS = 5

    private const val WORD_INSIDE_FRACTION = 0.5f
    private const val MATCH_MARGIN_X = 0.006f
    private const val MATCH_MARGIN_Y_FACTOR = 0.3f

    fun locate(type: DocumentType, pages: List<PageText>): List<LocatedPage> =
        pages.mapIndexed { index, page -> locatePage(type, page, index) }

    private fun numberPattern(type: DocumentType): Regex? = when (type) {
        DocumentType.PASSPORT_RF, DocumentType.DRIVER_LICENSE -> PASSPORT_OR_DL_NUMBER
        DocumentType.SNILS -> SNILS_NUMBER
        DocumentType.INN -> INN_NUMBER
        DocumentType.OTHER -> null
    }

    private fun locatePage(type: DocumentType, page: PageText, index: Int): LocatedPage {
        val role = PageRoleDetector.detect(type, page, index)
        val templates = DocumentTemplates.fieldsFor(role).groupBy { it.kind }
        val lines = TextLines.of(page)
        val isPassport = type == DocumentType.PASSPORT_RF
        val numberMatches = numberPattern(type)?.let { lines.find(it) }.orEmpty()
        val mrzAreas = if (isPassport) lines.machineReadableLines(MRZ_MIN_FILLERS) else emptyList()
        val codeAreas = if (isPassport) lines.find(DIVISION_CODE).map { it.area } else emptyList()
        val kinds = DocumentTemplates.kindsFor(type)

        val fields = mutableMapOf<FieldKind, LocatedField>()
        for (kind in kinds) {
            val kindTemplates = templates[kind].orEmpty()
            val field = when (kind) {
                FieldKind.NUMBER -> locateNumber(kindTemplates, numberMatches, mrzAreas, page)
                FieldKind.DIVISION_CODE -> LocatedField(
                    kindTemplates.map { extendWithWords(it.area, page) } + codeAreas + mrzAreas
                )
                // На фото текста нет: область шаблона как есть.
                FieldKind.PHOTO -> LocatedField(kindTemplates.map { it.area })
                FieldKind.RESIDENCE, FieldKind.BIRTHPLACE -> LocatedField(kindTemplates.map { extendWithWords(it.area, page) })
            }
            if (field.areas.isNotEmpty() || field.numbers.isNotEmpty()) fields[kind] = field
        }
        return LocatedPage(role, fields)
    }

    private fun locateNumber(
        templates: List<FieldTemplate>,
        matches: List<TextLines.Match>,
        mrzAreas: List<NormRect>,
        page: PageText,
    ): LocatedField {
        val consumed = mutableSetOf<TextLines.Match>()
        val occurrences = mutableListOf<NumberOccurrence>()
        for (template in templates) {
            when (template.numberLayout) {
                // Горизонтальные слова OCR внутри вертикальной полосы — мусор, по ним полосу не расширяем.
                NumberLayout.VERTICAL_STRIP ->
                    occurrences += NumberOccurrence(template.area, null, keepable = true, NumberLayout.VERTICAL_STRIP)

                NumberLayout.MACHINE_READABLE -> {
                    val area = NormRect.unionOf(listOf(template.area) + mrzAreas)!!
                    occurrences += NumberOccurrence(area, null, keepable = false, NumberLayout.MACHINE_READABLE)
                }

                NumberLayout.HORIZONTAL, null -> {
                    val zone = template.area.expanded(0.05f, 0.05f)
                    val inside = matches.filter { it.area.fractionInside(zone) >= WORD_INSIDE_FRACTION }
                    consumed += inside
                    val area = NormRect.unionOf(listOf(extendWithWords(template.area, page)) + inside.map { it.area })!!
                    // Точные цифры есть, только если внутри ровно одно вхождение номера.
                    val digits = inside.singleOrNull()?.lastDigits
                    occurrences += NumberOccurrence(area, digits, keepable = true, NumberLayout.HORIZONTAL)
                }
            }
        }
        // Номер, напечатанный вне шаблона (например, вверху страниц паспорта).
        for (match in matches - consumed) {
            occurrences += NumberOccurrence(match.area, match.lastDigits, keepable = true, NumberLayout.HORIZONTAL)
        }
        // Машиночитаемая зона вне шаблона (страница распознана не той ролью).
        val coveredByTemplate = occurrences.filter { it.layout == NumberLayout.MACHINE_READABLE }.map { it.area }
        for (mrz in mrzAreas) {
            if (coveredByTemplate.none { mrz.fractionInside(it) >= 0.99f }) {
                occurrences += NumberOccurrence(mrz, null, keepable = false, NumberLayout.MACHINE_READABLE)
            }
        }
        return LocatedField(areas = emptyList(), numbers = occurrences)
    }

    /** Область шаблона плюс слова, лежащие в ней больше чем наполовину. */
    private fun extendWithWords(area: NormRect, page: PageText): NormRect {
        val words = page.words.filter { it.box.fractionInside(area) >= WORD_INSIDE_FRACTION }
        return NormRect.unionOf(listOf(area) + words.map { it.box })!!
    }

    /** Строки OCR как последовательности символов с рамками — для регулярных выражений по номерам. */
    internal class TextLines private constructor(private val lines: List<List<CharRef>>) {

        /** [precise] = false, если рамка взята со всего слова, а не с символа. */
        class CharRef(val char: Char, val box: NormRect?, val precise: Boolean)

        class Match(val area: NormRect, val lastDigits: NormRect?)

        fun find(regex: Regex): List<Match> = lines.flatMap { chars ->
            val text = chars.joinToString("") { it.char.toString() }
            regex.findAll(text).mapNotNull { result ->
                val refs = chars.subList(result.range.first, result.range.last + 1)
                val boxes = refs.mapNotNull { it.box }
                val area = NormRect.unionOf(boxes) ?: return@mapNotNull null
                val lastFour = refs.filter { it.char.isDigit() }.takeLast(4)
                // Оставить видимыми можно только цифры с собственными рамками, иначе откроется всё слово.
                val lastDigits = if (lastFour.size == 4 && lastFour.all { it.precise && it.box != null }) {
                    NormRect.unionOf(lastFour.map { it.box!! })
                } else {
                    null
                }
                Match(area.expanded(MATCH_MARGIN_X, area.height * MATCH_MARGIN_Y_FACTOR), lastDigits)
            }.toList()
        }

        /** Строки машиночитаемой зоны: много символов-заполнителей «<». */
        fun machineReadableLines(minFillers: Int): List<NormRect> = lines.mapNotNull { chars ->
            val fillers = chars.count { it.char == '<' || it.char == '«' || it.char == '‹' }
            if (fillers < minFillers) return@mapNotNull null
            val area = NormRect.unionOf(chars.mapNotNull { it.box }) ?: return@mapNotNull null
            area.expanded(MATCH_MARGIN_X, area.height * MATCH_MARGIN_Y_FACTOR)
        }

        companion object {
            fun of(page: PageText): TextLines {
                val lines = page.words.groupBy { it.line }.toSortedMap().values.map { words ->
                    val chars = mutableListOf<CharRef>()
                    words.sortedBy { it.box.left }.forEach { word ->
                        if (chars.isNotEmpty()) chars += CharRef(' ', null, precise = false)
                        val symbolsMatchText = word.symbols.isNotEmpty() &&
                            word.symbols.joinToString("") { it.text } == word.text
                        if (symbolsMatchText) {
                            word.symbols.forEach { symbol ->
                                symbol.text.forEach { chars += CharRef(it, symbol.box, precise = symbol.text.length == 1) }
                            }
                        } else {
                            word.text.forEach { chars += CharRef(it, word.box, precise = false) }
                        }
                    }
                    chars
                }
                return TextLines(lines)
            }
        }
    }
}
