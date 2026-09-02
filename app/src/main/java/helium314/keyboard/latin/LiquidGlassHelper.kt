// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

object LiquidGlassHelper {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()

    fun getLiquidGlassIntensity(context: Context, isNight: Boolean): Float {
        val prefs = context.prefs()
        val key = if (isNight) Settings.PREF_LIQUID_GLASS_INTENSITY_NIGHT else Settings.PREF_LIQUID_GLASS_INTENSITY
        val defaultVal = Defaults.PREF_LIQUID_GLASS_INTENSITY
        return prefs.getInt(key, defaultVal) / 100f
    }

    fun setLiquidGlassIntensity(context: Context, intensity: Float, isNight: Boolean) {
        val prefs = context.prefs()
        val key = if (isNight) Settings.PREF_LIQUID_GLASS_INTENSITY_NIGHT else Settings.PREF_LIQUID_GLASS_INTENSITY
        val intVal = (intensity * 100).toInt().coerceIn(0, 100)
        prefs.edit().putInt(key, intVal).apply()
    }

    /**
     * Renders Apple-style 3D Liquid Glass keycaps with physical bottom shelf depth,
     * vertical ambient light illumination, liquid specular sheen, and crisp refraction rim.
     */
    @JvmStatic
    @JvmOverloads
    fun drawLiquidGlassKey(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        baseColor: Int,
        isPressed: Boolean,
        intensity: Float = 0.75f,
        isCircle: Boolean = false
    ) {
        if (rect.width() <= 0 || rect.height() <= 0) return

        val clampedIntensity = intensity.coerceIn(0.1f, 1.0f)
        val density = (rect.width() / 36f).coerceIn(1.0f, 3.0f)

        // 1. Apple / Clink Physical Bottom Shelf & Base Diffuse Shadow
        val shadowHeight = (1.20f * density).coerceIn(1.5f, 3.5f)
        val baseAlpha = Color.alpha(baseColor)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)
        val hasColorTint = hsl[1] > 0.15f
        val isDarkGlass = baseAlpha < 115 || ColorUtils.calculateLuminance(baseColor) <= 0.45

        val pressOffset = if (isPressed) shadowHeight * 0.70f else 0f
        val keyFace = RectF(
            rect.left,
            rect.top + pressOffset,
            rect.right,
            rect.bottom - (shadowHeight - pressOffset)
        )
        val faceRadius = Math.max(0f, cornerRadius - 0.5f)

        // Diffuse ambient shadow at the base to lift glass keys from keyboard canvas
        if (!isPressed) {
            val shadowRect = RectF(rect.left, rect.top + shadowHeight, rect.right, rect.bottom)
            val shadowAlpha = if (isDarkGlass) {
                (45 * clampedIntensity).toInt().coerceIn(0, 255)
            } else {
                (35 * clampedIntensity).toInt().coerceIn(0, 255)
            }
            shadowPaint.color = Color.argb(shadowAlpha, 0, 0, 0)
            if (isCircle) {
                val r = Math.min(shadowRect.width(), shadowRect.height()) * 0.5f
                canvas.drawCircle(shadowRect.centerX(), shadowRect.centerY(), r, shadowPaint)
            } else {
                canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
            }
        }

        // 2. Translucent Liquid Glass Body with Refraction Fill
        // Pressed state: translucent fill lights up for tactile touch illumination
        val activeAlpha = if (isPressed) {
            if (isDarkGlass) {
                ((baseAlpha * 1.8f) + 40).toInt().coerceIn(0, 255)
            } else {
                ((baseAlpha * 1.25f) + 30).toInt().coerceIn(0, 255)
            }
        } else {
            baseAlpha
        }

        val topColor: Int
        val botColor: Int
        if (hasColorTint) {
            val rawTop = ColorUtils.blendARGB(baseColor, Color.WHITE, (if (isPressed) 0.25f else 0.15f) * clampedIntensity)
            val rawBot = ColorUtils.blendARGB(baseColor, Color.BLACK, (if (isPressed) 0.05f else 0.10f) * clampedIntensity)
            topColor = ColorUtils.setAlphaComponent(rawTop, activeAlpha)
            botColor = ColorUtils.setAlphaComponent(rawBot, activeAlpha)
        } else if (isDarkGlass) {
            topColor = Color.argb((activeAlpha + 14).coerceIn(0, 255), 255, 255, 255)
            botColor = Color.argb((activeAlpha * 0.62f).toInt().coerceIn(0, 255), 255, 255, 255)
        } else {
            topColor = ColorUtils.setAlphaComponent(Color.WHITE, activeAlpha)
            botColor = Color.argb((activeAlpha * 0.88f).toInt().coerceIn(0, 255), 232, 238, 248)
        }

        val bodyShader = LinearGradient(
            keyFace.left, keyFace.top,
            keyFace.left, keyFace.bottom,
            topColor, botColor,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = bodyShader

        if (isCircle) {
            val r = Math.min(keyFace.width(), keyFace.height()) * 0.5f
            canvas.drawCircle(keyFace.centerX(), keyFace.centerY(), r, fillPaint)
        } else {
            canvas.drawRoundRect(keyFace, faceRadius, faceRadius, fillPaint)
        }

        // 3. Specular Liquid Sheen (Crisp Top Specular Highlight)
        val highlightHeight = keyFace.height() * (if (isPressed) 0.46f else 0.40f)
        val topSpecularAlpha = ((if (isPressed) 130 else 75) * clampedIntensity).toInt().coerceIn(0, 255)
        val midSpecularAlpha = (topSpecularAlpha * 0.32f).toInt().coerceIn(0, 255)

        val specularShader = LinearGradient(
            keyFace.left, keyFace.top,
            keyFace.left, keyFace.top + highlightHeight,
            intArrayOf(
                Color.argb(topSpecularAlpha, 255, 255, 255),
                Color.argb(midSpecularAlpha, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        specularPaint.shader = specularShader

        canvas.save()
        path.reset()
        if (isCircle) {
            val r = Math.min(keyFace.width(), keyFace.height()) * 0.5f
            path.addCircle(keyFace.centerX(), keyFace.centerY(), r, Path.Direction.CW)
        } else {
            path.addRoundRect(keyFace, faceRadius, faceRadius, Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawRect(keyFace.left, keyFace.top, keyFace.right, keyFace.top + highlightHeight, specularPaint)
        canvas.restore()

        // 4. Refraction Glass Rim (Crisp 1dp Beveled Specular Border)
        val strokeWidth = (1.0f * density.coerceIn(1.0f, 1.4f)).coerceIn(1.0f, 1.75f)
        strokePaint.strokeWidth = strokeWidth

        val topRimAlpha: Int
        val sideRimAlpha: Int
        val botRimColor: Int
        if (isDarkGlass) {
            topRimAlpha = ((if (isPressed) 210 else 105) * clampedIntensity).toInt().coerceIn(0, 255)
            sideRimAlpha = (topRimAlpha * 0.55f).toInt().coerceIn(0, 255)
            val botRimAlpha = (topRimAlpha * 0.42f).toInt().coerceIn(0, 255)
            botRimColor = Color.argb(botRimAlpha, 255, 255, 255)
        } else {
            topRimAlpha = ((if (isPressed) 220 else 165) * clampedIntensity).toInt().coerceIn(0, 255)
            sideRimAlpha = (topRimAlpha * 0.45f).toInt().coerceIn(0, 255)
            val botRimAlpha = ((if (isPressed) 50 else 30) * clampedIntensity).toInt().coerceIn(0, 255)
            botRimColor = Color.argb(botRimAlpha, 0, 0, 0)
        }

        val rimShader = LinearGradient(
            keyFace.left, keyFace.top,
            keyFace.left, keyFace.bottom,
            intArrayOf(
                Color.argb(topRimAlpha, 255, 255, 255),
                Color.argb(sideRimAlpha, 255, 255, 255),
                botRimColor
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = rimShader

        val inset = strokeWidth * 0.5f
        val strokeRect = RectF(
            keyFace.left + inset,
            keyFace.top + inset,
            keyFace.right - inset,
            keyFace.bottom - inset
        )
        val strokeRadius = Math.max(0f, faceRadius - inset)

        if (isCircle) {
            val r = Math.min(strokeRect.width(), strokeRect.height()) * 0.5f
            canvas.drawCircle(strokeRect.centerX(), strokeRect.centerY(), r, strokePaint)
        } else {
            canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, strokePaint)
        }
    }

    /**
     * Applies Liquid Glass background styling to a standard button / View (toolbar keys, etc.)
     */
    fun applyLiquidGlassToView(view: View, baseColor: Int, cornerRadiusPx: Float, intensity: Float = 0.75f) {
        val clampedIntensity = intensity.coerceIn(0.1f, 1.0f)
        val strokeColor = Color.argb((175 * clampedIntensity).toInt(), 255, 255, 255)
        val strokeWidth = (view.resources.displayMetrics.density * 1.0f).toInt().coerceAtLeast(1)

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(baseColor)
            setStroke(strokeWidth, strokeColor)
        }
        view.background = drawable
    }
}
