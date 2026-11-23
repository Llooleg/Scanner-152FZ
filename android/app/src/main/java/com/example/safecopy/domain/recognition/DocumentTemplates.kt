package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.model.DocumentType

/** Поля, которые пользователь может скрыть переключателями. */
enum class FieldKind { RESIDENCE, NUMBER, DIVISION_CODE, BIRTHPLACE, PHOTO }

/** Какая это страница документа: от неё зависит шаблон разметки. */
enum class PageRole {
    /** Разворот паспорта, стр. 2–3: вверху «Паспорт выдан», внизу фото и личные данные. */
    PASSPORT_SPREAD,

    /** Одна страница 2 паспорта: кем и когда выдан, код подразделения. */
    PASSPORT_ISSUE,

    /** Одна страница 3 паспорта: фото, ФИО, место рождения, машиночитаемая зона. */
    PASSPORT_PERSONAL,

    /** Альбомная страница паспорта, которую не удалось опознать: применяются шаблоны стр. 2 и стр. 3 сразу. */
    PASSPORT_PAGE_UNKNOWN,

    /** Страницы «Место жительства». */
    PASSPORT_REGISTRATION,
    SNILS,
    DRIVER_FRONT,
    DRIVER_BACK,
    INN,
    OTHER,
}

/** Как найти последние 4 цифры номера в этой области шаблона. */
enum class NumberLayout {
    /** Номер напечатан вертикально вдоль края (паспорт РФ): нужен отдельный OCR повёрнутой полосы. */
    VERTICAL_STRIP,

    /** Номер напечатан горизонтально: цифры ищутся в OCR страницы. */
    HORIZONTAL,

    /** Машиночитаемая зона: в ней зашифрованы номер и код подразделения, оставлять в ней ничего нельзя. */
    MACHINE_READABLE,
}

data class FieldTemplate(
    val kind: FieldKind,
    val area: NormRect,
    val numberLayout: NumberLayout? = null,
)

/**
 * Шаблоны разметки в долях «прямой» страницы.
 *
 * Координаты приблизительные: они сняты с образцов бланков, а не с измерений конкретных документов,
 * поэтому области заданы с запасом, а OCR их только расширяет. Итог пользователь видит перед отправкой
 * и может закрасить пропущенное вручную.
 */
object DocumentTemplates {

    // Разворот паспорта стр. 2–3 (125 × 176 мм): верхняя половина — стр. 2, нижняя — стр. 3.
    private val SPREAD_UPPER = listOf(
        FieldTemplate(FieldKind.DIVISION_CODE, NormRect(0.42f, 0.22f, 0.90f, 0.34f)),
        FieldTemplate(FieldKind.NUMBER, NormRect(0.86f, 0.01f, 1.00f, 0.49f), NumberLayout.VERTICAL_STRIP),
    )
    private val SPREAD_LOWER = listOf(
        FieldTemplate(FieldKind.PHOTO, NormRect(0.02f, 0.54f, 0.35f, 0.87f)),
        FieldTemplate(FieldKind.BIRTHPLACE, NormRect(0.33f, 0.73f, 0.88f, 0.89f)),
        FieldTemplate(FieldKind.NUMBER, NormRect(0.86f, 0.51f, 1.00f, 0.99f), NumberLayout.VERTICAL_STRIP),
        FieldTemplate(FieldKind.NUMBER, NormRect(0.00f, 0.88f, 1.00f, 1.00f), NumberLayout.MACHINE_READABLE),
        FieldTemplate(FieldKind.DIVISION_CODE, NormRect(0.00f, 0.88f, 1.00f, 1.00f), NumberLayout.MACHINE_READABLE),
    )

    /** Отдельная страница — половина разворота, растянутая по высоте. */
    private fun halfOfSpread(fields: List<FieldTemplate>, fromTop: Float) = fields.map { field ->
        val a = field.area
        field.copy(
            area = NormRect(
                a.left,
                ((a.top - fromTop) * 2).coerceIn(0f, 1f),
                a.right,
                ((a.bottom - fromTop) * 2).coerceIn(0f, 1f),
            )
        )
    }

    private val TEMPLATES: Map<PageRole, List<FieldTemplate>> = mapOf(
        PageRole.PASSPORT_SPREAD to SPREAD_UPPER + SPREAD_LOWER,
        PageRole.PASSPORT_ISSUE to halfOfSpread(SPREAD_UPPER, fromTop = 0f),
        PageRole.PASSPORT_PERSONAL to halfOfSpread(SPREAD_LOWER, fromTop = 0.5f),
        PageRole.PASSPORT_PAGE_UNKNOWN to halfOfSpread(SPREAD_UPPER, 0f) + halfOfSpread(SPREAD_LOWER, 0.5f),
        // Штампы о регистрации занимают всю страницу; оставляем только заголовок.
        PageRole.PASSPORT_REGISTRATION to listOf(FieldTemplate(FieldKind.RESIDENCE, NormRect(0f, 0.06f, 1f, 1f))),
        // Зелёная карточка СНИЛС (альбомная).
        PageRole.SNILS to listOf(
            FieldTemplate(FieldKind.NUMBER, NormRect(0.12f, 0.10f, 0.88f, 0.32f), NumberLayout.HORIZONTAL),
            FieldTemplate(FieldKind.BIRTHPLACE, NormRect(0.04f, 0.52f, 0.96f, 0.84f)),
        ),
        // Лицевая сторона ВУ образца 2014 г.: фото слева, поля 1–9 справа.
        PageRole.DRIVER_FRONT to listOf(
            FieldTemplate(FieldKind.PHOTO, NormRect(0.02f, 0.18f, 0.33f, 0.82f)),
            FieldTemplate(FieldKind.BIRTHPLACE, NormRect(0.32f, 0.32f, 0.99f, 0.46f)),
            FieldTemplate(FieldKind.NUMBER, NormRect(0.32f, 0.58f, 0.85f, 0.73f), NumberLayout.HORIZONTAL),
            FieldTemplate(FieldKind.RESIDENCE, NormRect(0.32f, 0.71f, 0.99f, 0.84f)),
        ),
        PageRole.DRIVER_BACK to emptyList(),
        // Свидетельство ИНН (лист A4).
        PageRole.INN to listOf(
            FieldTemplate(FieldKind.BIRTHPLACE, NormRect(0.04f, 0.33f, 0.96f, 0.48f)),
            FieldTemplate(FieldKind.NUMBER, NormRect(0.10f, 0.52f, 0.90f, 0.68f), NumberLayout.HORIZONTAL),
        ),
        PageRole.OTHER to emptyList(),
    )

    fun fieldsFor(role: PageRole): List<FieldTemplate> = TEMPLATES.getValue(role)

    /** Какие поля вообще бывают у документа этого типа (для переключателей). */
    fun kindsFor(type: DocumentType): Set<FieldKind> = when (type) {
        DocumentType.PASSPORT_RF -> setOf(FieldKind.RESIDENCE, FieldKind.NUMBER, FieldKind.DIVISION_CODE, FieldKind.BIRTHPLACE, FieldKind.PHOTO)
        DocumentType.SNILS -> setOf(FieldKind.NUMBER, FieldKind.BIRTHPLACE)
        DocumentType.DRIVER_LICENSE -> setOf(FieldKind.NUMBER, FieldKind.BIRTHPLACE, FieldKind.RESIDENCE, FieldKind.PHOTO)
        DocumentType.INN -> setOf(FieldKind.NUMBER, FieldKind.BIRTHPLACE)
        DocumentType.OTHER -> emptySet()
    }
}

/** Определяет роль страницы по её тексту, пропорциям и номеру. */
object PageRoleDetector {

    private val ISSUE = listOf("паспорт выдан", "дата выдачи", "код подразделения", "личный код")
    private val PERSONAL = listOf("фамилия", "отчество", "место рождения", "дата рождения")
    private val REGISTRATION = listOf("место жительства", "зарегистрирован", "снят с регистрационного учета")
    private val DRIVER_FRONT = listOf("водительское удостоверение")
    private val DRIVER_FRONT_LATIN = listOf("driving licence", "permis de conduire")
    private val DRIVER_BACK = listOf("особые отметки")

    fun detect(type: DocumentType, page: PageText, index: Int): PageRole {
        val text = NormalizedText.of(page.text)
        fun count(phrases: List<String>, latin: Boolean = false) = phrases.count { text.contains(it, latin) }
        return when (type) {
            DocumentType.PASSPORT_RF -> {
                val issue = count(ISSUE)
                val personal = count(PERSONAL) + if (page.text.uppercase().contains("PNRUS")) 2 else 0
                val registration = count(REGISTRATION)
                when {
                    registration > maxOf(issue, personal) -> PageRole.PASSPORT_REGISTRATION
                    // Текст не прочитан. Первая страница обычно разворот, следующие — прописка.
                    // Ошибка в эту сторону закрасит лишнее, а не оставит адрес открытым.
                    issue == 0 && personal == 0 && index > 0 -> PageRole.PASSPORT_REGISTRATION
                    // Разворот стоит «портретом», отдельная страница — «альбомом».
                    page.aspect > 1.1f -> PageRole.PASSPORT_SPREAD
                    issue > personal -> PageRole.PASSPORT_ISSUE
                    personal > issue -> PageRole.PASSPORT_PERSONAL
                    else -> PageRole.PASSPORT_PAGE_UNKNOWN
                }
            }

            DocumentType.DRIVER_LICENSE -> {
                val front = count(DRIVER_FRONT) + count(DRIVER_FRONT_LATIN, latin = true)
                val back = count(DRIVER_BACK)
                when {
                    front > 0 -> PageRole.DRIVER_FRONT
                    back > 0 -> PageRole.DRIVER_BACK
                    index == 0 -> PageRole.DRIVER_FRONT
                    else -> PageRole.DRIVER_BACK
                }
            }

            DocumentType.SNILS -> PageRole.SNILS
            DocumentType.INN -> PageRole.INN
            DocumentType.OTHER -> PageRole.OTHER
        }
    }
}
