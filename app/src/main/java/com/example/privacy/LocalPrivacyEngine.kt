package com.example.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.data.model.BoundingBox
import com.example.data.model.RedactionRegion
import java.security.MessageDigest
import java.util.regex.Pattern

object LocalPrivacyEngine {

    // Common PII patterns for on-device heuristic detection
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_PATTERN = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")
    private val SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    private val CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b")

    /**
     * Detects sensitive personal identifying information from OCR or visible text strings.
     */
    fun detectSensitiveText(text: String): List<String> {
        val detected = mutableListOf<String>()
        if (EMAIL_PATTERN.matcher(text).find()) detected.add("Email address")
        if (PHONE_PATTERN.matcher(text).find()) detected.add("Phone number")
        if (SSN_PATTERN.matcher(text).find()) detected.add("ID / SSN number")
        if (CREDIT_CARD_PATTERN.matcher(text).find()) detected.add("Financial card number")
        return detected
    }

    /**
     * Computes a privacy-preserving SHA-256 hash of a normalized claim text.
     */
    fun hashClaimText(text: String): String {
        val normalized = text.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sanitizes a bitmap before cloud dispatch by burning solid blackout / blur rectangles
     * over user-selected or auto-detected redaction regions.
     */
    fun redactBitmap(source: Bitmap, regions: List<RedactionRegion>): Bitmap {
        if (regions.isEmpty()) return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)

        val output = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }

        val width = output.width.toFloat()
        val height = output.height.toFloat()

        for (region in regions) {
            if (!region.isApplied) continue
            val rect = RectF(
                region.left * width,
                region.top * height,
                region.right * width,
                region.bottom * height
            )
            canvas.drawRect(rect, paint)
            canvas.drawRect(rect, borderPaint)
            canvas.drawText("REDACTED: ${region.label}", rect.left + 12f, (rect.centerY() + 10f).coerceIn(rect.top + 20f, rect.bottom - 10f), textPaint)
        }

        return output
    }

    /**
     * Crops the frame to eliminate Android status bar (top 4%) and navigation bar (bottom 4%)
     * if requested to prevent accidental transmission of private system tray data.
     */
    fun cropSystemDecorations(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val topMargin = (height * 0.045f).toInt()
        val bottomMargin = (height * 0.045f).toInt()
        val contentHeight = (height - topMargin - bottomMargin).coerceAtLeast(100)

        return Bitmap.createBitmap(bitmap, 0, topMargin, width, contentHeight)
    }

    /**
     * Generates standard safe default redaction zones (e.g. top status bar and username header)
     */
    fun getDefaultStatusRedaction(): RedactionRegion {
        return RedactionRegion(
            left = 0.0f,
            top = 0.0f,
            right = 1.0f,
            bottom = 0.045f,
            label = "System Status Bar (Clock/Icons)",
            isAutoDetected = true,
            isApplied = true
        )
    }
}
