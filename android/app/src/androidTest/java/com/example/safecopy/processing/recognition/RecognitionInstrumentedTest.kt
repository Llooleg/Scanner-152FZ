package com.example.safecopy.processing.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.safecopy.domain.model.DocumentType
import com.example.safecopy.domain.recognition.FieldKind
import com.example.safecopy.domain.recognition.FieldLocator
import com.example.safecopy.domain.recognition.MaskComposer
import com.example.safecopy.domain.recognition.MaskSettings
import com.example.safecopy.domain.recognition.NormRect
import com.example.safecopy.domain.recognition.NumberLayout
import com.example.safecopy.domain.recognition.NumberMode
import com.example.safecopy.domain.recognition.PageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Настоящий Tesseract на синтетическом развороте паспорта, нарисованном в тесте (вымышленные данные).
 * Проверяет распознавание типа, выпрямление повёрнутой страницы, поиск последних цифр на вертикальной
 * полосе и то, что маска действительно впечатывается в пиксели.
 */
@RunWith(AndroidJUnit4::class)
class RecognitionInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val textRecognizer = TesseractTextRecognizer(TessDataInstaller(context))
    private val recognizer = DocumentRecognizer(textRecognizer)
    private val stripReader = NumberStripReader(textRecognizer)

    private val specimen = Specimen.draw()

    @Test
    fun specimenIsRecognizedAsPassport() = runBlocking {
        val recognition = recognizer.recognize(listOf(specimen.bitmap))
        assertEquals(DocumentType.PASSPORT_RF, recognition.detectedType)
        assertEquals(0, recognition.pages.single().rotationDegrees)
    }

    @Test
    fun turnedPagesAreStraightened() = runBlocking {
        // Страницу повернули на N° по часовой — выпрямляется поворотом на (360 − N)°.
        for (turn in listOf(90, 180, 270)) {
            val recognition = recognizer.recognize(listOf(specimen.bitmap.rotatedCopy(turn)))
            assertEquals("поворот $turn", DocumentType.PASSPORT_RF, recognition.detectedType)
            assertEquals("поворот $turn", (360 - turn) % 360, recognition.pages.single().rotationDegrees)
        }
    }

    @Test
    fun lastFourDigitsAreFoundOnVerticalStrip() = runBlocking {
        val strip = NormRect(0.86f, 0.01f, 1.00f, 0.49f)
        val digits = stripReader.findLastDigits(specimen.bitmap, strip)
        assertNotNull("Последние цифры не найдены", digits)
        val hole = digits!!
        assertTrue("Рамка вне полосы: $hole", hole.fractionInside(strip.expanded(0.01f, 0.01f)) > 0.99f)
        // Рамка — над «3456», а не над серией «45 06».
        assertTrue("Рамка не на последних цифрах: $hole", hole.centerY in specimen.lastDigitsY)
        assertTrue("Рамка задевает серию: $hole", hole.bottom < specimen.seriesY.start)
    }

    @Test
    fun fullPipelineMasksPixels() = runBlocking {
        val recognition = recognizer.recognize(listOf(specimen.bitmap))
        var located = FieldLocator.locate(DocumentType.PASSPORT_RF, recognition.pages).single()
        assertEquals(PageRole.PASSPORT_SPREAD, located.role)
        val found = located.fields.getValue(FieldKind.NUMBER).numbers
            .filter { it.layout == NumberLayout.VERTICAL_STRIP }
            .associate { it.area to stripReader.findLastDigits(specimen.bitmap, it.area) }
        located = located.mapNumbers { n -> if (n.layout == NumberLayout.VERTICAL_STRIP) n.copy(lastDigits = found[n.area]) else n }

        val settings = MaskSettings(
            hidden = setOf(FieldKind.NUMBER, FieldKind.DIVISION_CODE, FieldKind.BIRTHPLACE),
            numberMode = NumberMode.KEEP_LAST_4,
        )
        val mask = MaskComposer.compose(located, settings, emptyList())
        val out = MaskRenderer.render(specimen.bitmap, mask.rects)

        assertTrue("Код подразделения виден", isBlack(out, specimen.divisionCode))
        assertTrue("Место рождения видно", isBlack(out, specimen.birthplace))
        assertTrue("Серия видна", isBlack(out, specimen.series))
        assertTrue("Машиночитаемая зона видна", isBlack(out, specimen.mrz))
        assertTrue("Последние 4 цифры закрашены, хотя должны остаться", !isBlack(out, specimen.lastDigits))
        assertTrue("Фото закрашено, хотя по настройкам видно", !isBlack(out, specimen.photo))
    }

    @Test
    fun rendererFillsSolidBlackAndKeepsTheRest() {
        val page = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = MaskRenderer.render(page, listOf(NormRect(0.25f, 0.25f, 0.5f, 0.5f)))
        assertEquals(Color.BLACK, out.getPixel(60, 30))
        assertEquals(Color.WHITE, out.getPixel(150, 80))
        assertEquals("Оригинал не должен меняться", Color.WHITE, page.getPixel(60, 30))
    }

    /** Все пиксели в центральной части области чёрные. */
    private fun isBlack(bitmap: Bitmap, area: NormRect): Boolean {
        val inner = NormRect(
            area.left + area.width * 0.2f, area.top + area.height * 0.2f,
            area.right - area.width * 0.2f, area.bottom - area.height * 0.2f,
        )
        for (y in (inner.top * bitmap.height).toInt() until (inner.bottom * bitmap.height).toInt()) {
            for (x in (inner.left * bitmap.width).toInt() until (inner.right * bitmap.width).toInt()) {
                if (bitmap.getPixel(x, y) != Color.BLACK) return false
            }
        }
        return true
    }

    /** Синтетический разворот стр. 2–3 и координаты нарисованных полей в долях страницы. */
    private class Specimen(
        val bitmap: Bitmap,
        val divisionCode: NormRect,
        val birthplace: NormRect,
        val photo: NormRect,
        val series: NormRect,
        val lastDigits: NormRect,
        val mrz: NormRect,
        val seriesY: ClosedFloatingPointRange<Float>,
        val lastDigitsY: ClosedFloatingPointRange<Float>,
    ) {
        companion object {
            private const val W = 1250
            private const val H = 1760

            fun draw(): Specimen {
                val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                val canvas = Canvas(bitmap)
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 34f }
                fun line(s: String, x: Float, y: Float): NormRect {
                    canvas.drawText(s, x, y, text)
                    return NormRect(x / W, (y - text.textSize) / H, (x + text.measureText(s)) / W, (y + 8) / H)
                }
                fun valueAfter(label: String, value: String, x: Float, y: Float): NormRect {
                    line("$label $value", x, y)
                    val start = x + text.measureText("$label ")
                    return NormRect(start / W, (y - text.textSize) / H, (start + text.measureText(value)) / W, (y + 8) / H)
                }

                line("РОССИЙСКАЯ ФЕДЕРАЦИЯ", 330f, 70f)
                line("Паспорт выдан ОТДЕЛОМ УФМС РОССИИ", 60f, 170f)
                line("ПО ГОР. МОСКВЕ В РАЙОНЕ ТЕСТОВОМ", 60f, 220f)
                line("Дата выдачи 01.02.2015", 60f, 470f)
                val code = valueAfter("Код подразделения", "770-001", 560f, 470f)

                val photoPx = android.graphics.RectF(50f, 960f, 410f, 1480f)
                canvas.drawRect(photoPx, Paint().apply { color = Color.rgb(150, 150, 150) })
                line("Фамилия ИВАНОВ", 470f, 1000f)
                line("Имя ИВАН", 470f, 1070f)
                line("Отчество ИВАНОВИЧ", 470f, 1140f)
                line("Дата рождения 01.01.1990", 470f, 1210f)
                val birth = valueAfter("Место рождения", "ГОР. МОСКВА", 470f, 1360f)

                val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f; typeface = Typeface.MONOSPACE }
                val mrzText = "PNRUSIVANOV<<IVAN<IVANOVICH<<<<<<<<<<<<<<<"
                canvas.drawText(mrzText, 40f, 1680f, mono)
                val mrz = NormRect(40f / W, (1680f - 30f) / H, (40f + mono.measureText(mrzText)) / W, 1690f / H)

                // Серия и номер красным вертикально вдоль правого края, читается снизу вверх.
                val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 30, 30); textSize = 44f }
                val number = "45 06 123456"
                val baseX = 1180f
                val startY = 780f
                canvas.save()
                canvas.rotate(-90f, baseX, startY)
                canvas.drawText(number, baseX, startY, red)
                canvas.restore()
                val total = red.measureText(number)
                val seriesLen = red.measureText("45 06")
                val prefixLen = red.measureText("45 06 12")
                val stripX = NormRect((baseX - red.textSize) / W, 0f, (baseX + 8) / W, 1f)
                val series = NormRect(stripX.left, (startY - seriesLen) / H, stripX.right, startY / H)
                val last = NormRect(stripX.left, (startY - total) / H, stripX.right, (startY - prefixLen) / H)

                return Specimen(
                    bitmap = bitmap,
                    divisionCode = code,
                    birthplace = birth,
                    photo = NormRect(photoPx.left / W, photoPx.top / H, photoPx.right / W, photoPx.bottom / H),
                    series = series,
                    lastDigits = last,
                    mrz = mrz,
                    seriesY = series.top..series.bottom,
                    lastDigitsY = last.top..last.bottom,
                )
            }
        }
    }
}
