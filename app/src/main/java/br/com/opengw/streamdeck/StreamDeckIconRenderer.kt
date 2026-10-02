package br.com.opengw.streamdeck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream

/** What a key should currently show -- glyph plus optional level bars and a value label. */
internal data class StreamDeckIconState(
    val glyph: StreamDeckGlyph,
    val label: String? = null,
    val level: Int? = null,
    val maxLevel: Int? = null,
    val active: Boolean = true
)

internal enum class StreamDeckGlyph { AC_UP, AC_DOWN, VOLUME_UP, VOLUME_DOWN, SEAT_VENT, POWER, SPORT_PLUS, TEMP_DISPLAY, BLANK }

/**
 * Draws each key's icon in code (no bundled image assets) so the "blue bar" level feedback the
 * user asked for can be composited on top of the same glyph on every redraw. Matches the
 * StreamDock293s image format pinned by the vendored SDK: 85x85, JPEG, rotated 90 degrees.
 */
internal object StreamDeckIconRenderer {
    /** Canvas the glyphs are drawn on; scaled to the real per-key size (85 main keys / 80 side strip) at the end. */
    private const val SIZE = 85
    private val BACKGROUND = Color.BLACK
    private val GLYPH_COLOR_INACTIVE = Color.rgb(95, 95, 102)
    private val BAR_ACTIVE = Color.rgb(64, 156, 255)
    private val BAR_INACTIVE = Color.rgb(50, 50, 56)

    /** Signature color per function, shown at full strength when [StreamDeckIconState.active]. */
    private fun accentFor(glyph: StreamDeckGlyph): Int = when (glyph) {
        StreamDeckGlyph.AC_UP -> Color.rgb(255, 145, 80)
        StreamDeckGlyph.AC_DOWN -> Color.rgb(90, 175, 255)
        StreamDeckGlyph.VOLUME_UP, StreamDeckGlyph.VOLUME_DOWN -> Color.rgb(130, 225, 150)
        StreamDeckGlyph.SEAT_VENT -> Color.rgb(90, 220, 220)
        StreamDeckGlyph.POWER -> Color.rgb(235, 235, 240)
        StreamDeckGlyph.SPORT_PLUS -> Color.rgb(255, 70, 70)
        StreamDeckGlyph.TEMP_DISPLAY -> Color.rgb(255, 175, 70)
        StreamDeckGlyph.BLANK -> GLYPH_COLOR_INACTIVE
    }

    /**
     * @param sizePx Real pixel size expected by the hardware for this key: 85 for the main 3x5
     * grid, 80 for the side-strip keys (16-18) -- confirmed against the vendor SDK's
     * `key_image_format` (85x85) vs `secondscreen_image_format` (80x80). Sending 85x85 for a
     * side-strip key was the cause of the visible border/misalignment on those three cells.
     */
    fun renderJpeg(state: StreamDeckIconState, sizePx: Int = SIZE, quality: Int = 95): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val glyphColor = if (state.active) accentFor(state.glyph) else GLYPH_COLOR_INACTIVE
        val hasBars = state.level != null && state.maxLevel != null && state.maxLevel > 0
        val glyphArea = if (hasBars) RectF(6f, 4f, SIZE - 6f, SIZE - 26f) else RectF(6f, 6f, SIZE - 6f, SIZE - 6f)
        drawGlyph(canvas, state.glyph, glyphArea, glyphColor)

        if (hasBars) {
            drawLevelBars(canvas, state.level!!, state.maxLevel!!)
        } else if (state.label != null) {
            drawLabel(canvas, state.label, glyphColor)
        }

        val sized = if (sizePx != SIZE) Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true) else bitmap
        if (sized !== bitmap) bitmap.recycle()
        val rotated = rotate90(sized)
        sized.recycle()
        val out = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
        rotated.recycle()
        return out.toByteArray()
    }

    private fun rotate90(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun drawLevelBars(canvas: Canvas, level: Int, maxLevel: Int) {
        val barCount = maxLevel.coerceIn(1, 6)
        val shownLevel = level.coerceIn(0, maxLevel)
        val top = SIZE - 20f
        val bottom = SIZE - 6f
        val gap = 3f
        val totalGap = gap * (barCount - 1)
        val barWidth = (SIZE - 12f - totalGap) / barCount
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until barCount) {
            val filled = i < Math.round(shownLevel.toFloat() / maxLevel * barCount)
            paint.color = if (filled) BAR_ACTIVE else BAR_INACTIVE
            val left = 6f + i * (barWidth + gap)
            canvas.drawRoundRect(RectF(left, top, left + barWidth, bottom), 2f, 2f, paint)
        }
    }

    private fun drawLabel(canvas: Canvas, label: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            textSize = 15f
            isFakeBoldText = true
        }
        canvas.drawText(label, SIZE / 2f, SIZE - 10f, paint)
    }

    private fun drawGlyph(canvas: Canvas, glyph: StreamDeckGlyph, area: RectF, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        val cx = area.centerX()
        val cy = area.centerY()
        val r = minOf(area.width(), area.height()) / 2.5f

        when (glyph) {
            StreamDeckGlyph.AC_UP, StreamDeckGlyph.AC_DOWN -> {
                // Snowflake: three double-line branches, each with small forked tips.
                paint.strokeWidth = 3.2f
                for (angle in intArrayOf(0, 60, 120)) {
                    val rad = Math.toRadians(angle.toDouble())
                    val dx = (r * Math.cos(rad)).toFloat()
                    val dy = (r * Math.sin(rad)).toFloat()
                    canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
                    for (sign in intArrayOf(-1, 1)) {
                        val tipX = cx + sign * dx
                        val tipY = cy + sign * dy
                        val backX = cx + sign * dx * 0.62f
                        val backY = cy + sign * dy * 0.62f
                        val perpX = -dy / r * r * 0.22f
                        val perpY = dx / r * r * 0.22f
                        canvas.drawLine(backX - perpX, backY - perpY, tipX, tipY, paint)
                        canvas.drawLine(backX + perpX, backY + perpY, tipX, tipY, paint)
                    }
                }
                drawArrow(canvas, cx, cy + r + 11f, up = glyph == StreamDeckGlyph.AC_UP, color = color)
            }
            StreamDeckGlyph.VOLUME_UP, StreamDeckGlyph.VOLUME_DOWN -> {
                val speakerPaint = Paint(paint).apply { style = Paint.Style.FILL }
                val bodyLeft = cx - r
                val bodyRight = cx - r * 0.15f
                val bodyPath = android.graphics.Path().apply {
                    moveTo(bodyLeft + r * 0.25f, cy - r * 0.3f)
                    lineTo(bodyRight, cy - r * 0.3f)
                    lineTo(bodyRight, cy + r * 0.3f)
                    lineTo(bodyLeft + r * 0.25f, cy + r * 0.3f)
                    close()
                }
                canvas.drawPath(bodyPath, speakerPaint)
                canvas.drawRoundRect(RectF(bodyLeft, cy - r * 0.15f, bodyLeft + r * 0.3f, cy + r * 0.15f), 2f, 2f, speakerPaint)
                val trianglePath = android.graphics.Path().apply {
                    moveTo(bodyRight, cy - r * 0.32f)
                    lineTo(cx + r * 0.3f, cy - r)
                    lineTo(cx + r * 0.3f, cy + r)
                    lineTo(bodyRight, cy + r * 0.32f)
                    close()
                }
                canvas.drawPath(trianglePath, speakerPaint)
                paint.strokeWidth = 3.2f
                canvas.drawArc(RectF(cx + r * 0.35f, cy - r * 0.7f, cx + r * 1.3f, cy + r * 0.7f), -50f, 100f, false, paint)
                if (glyph == StreamDeckGlyph.VOLUME_UP) {
                    canvas.drawArc(RectF(cx + r * 0.7f, cy - r * 1.05f, cx + r * 1.7f, cy + r * 1.05f), -45f, 90f, false, paint)
                } else {
                    val mutePaint = Paint(paint).apply { strokeCap = Paint.Cap.ROUND }
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.5f, cx + r * 1.15f, cy + r * 0.5f, mutePaint)
                }
            }
            StreamDeckGlyph.SEAT_VENT -> {
                // Three-blade fan, like an actual ventilation icon.
                val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
                canvas.save()
                canvas.translate(cx, cy)
                repeat(3) { i ->
                    canvas.save()
                    canvas.rotate(i * 120f)
                    val blade = android.graphics.Path().apply {
                        moveTo(0f, 0f)
                        quadTo(r * 0.78f, -r * 0.5f, r * 0.18f, -r * 0.98f)
                        quadTo(-r * 0.12f, -r * 0.55f, 0f, 0f)
                        close()
                    }
                    canvas.drawPath(blade, bladePaint)
                    canvas.restore()
                }
                canvas.restore()
                val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = BACKGROUND; style = Paint.Style.FILL }
                canvas.drawCircle(cx, cy, r * 0.16f, hubPaint)
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawCircle(cx, cy, r * 0.16f, ringPaint)
            }
            StreamDeckGlyph.POWER -> {
                canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -60f, 300f, false, paint)
                canvas.drawLine(cx, cy - r * 1.15f, cx, cy - r * 0.2f, paint)
            }
            StreamDeckGlyph.SPORT_PLUS -> {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    textAlign = Paint.Align.CENTER
                    textSize = area.height() * 0.55f
                    isFakeBoldText = true
                }
                val fm = textPaint.fontMetrics
                val textY = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText("S+", cx, textY, textPaint)
            }
            StreamDeckGlyph.TEMP_DISPLAY -> {
                // Thermometer: a vertical stem with a filled bulb at the bottom.
                val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.38f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(cx, cy - r * 0.9f, cx, cy + r * 0.35f, stemPaint)
                val bulbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy + r * 0.55f, r * 0.42f, bulbPaint)
            }
            StreamDeckGlyph.BLANK -> Unit
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, up: Boolean, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val size = 7f
        val dir = if (up) -1f else 1f
        val path = android.graphics.Path().apply {
            moveTo(cx - size, cy - dir * size)
            lineTo(cx + size, cy - dir * size)
            lineTo(cx, cy + dir * size)
            close()
        }
        canvas.drawPath(path, paint)
    }
}
