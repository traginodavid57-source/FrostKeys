// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
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

    private val pressGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
     * Renders high-quality 3D Liquid Glass on a key with specular highlight,
     * refractive edge borders, and press reaction glow.
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
        val density = rect.width() / 40f // approx scaling factor

        // 1. Draw Translucent Base Body
        val effectiveBaseColor = if (isPressed) {
            ColorUtils.blendARGB(baseColor, Color.WHITE, 0.18f * clampedIntensity)
        } else {
            baseColor
        }
        fillPaint.color = effectiveBaseColor
        if (isCircle) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val r = Math.min(rect.width(), rect.height()) * 0.5f
            canvas.drawCircle(cx, cy, r, fillPaint)
        } else {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        }

        // 2. Specular Top Glare (Reflective Glass Sheen)
        val topHighlightHeight = rect.height() * (if (isPressed) 0.55f else 0.48f)
        val topSpecularAlpha = ((if (isPressed) 160 else 115) * clampedIntensity).toInt().coerceIn(0, 255)
        val midSpecularAlpha = ((if (isPressed) 60 else 30) * clampedIntensity).toInt().coerceIn(0, 255)

        val specularShader = LinearGradient(
            rect.left,
            rect.top,
            rect.left,
            rect.top + topHighlightHeight,
            intArrayOf(
                Color.argb(topSpecularAlpha, 255, 255, 255),
                Color.argb(midSpecularAlpha, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        specularPaint.shader = specularShader

        if (isCircle) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val r = Math.min(rect.width(), rect.height()) * 0.5f
            canvas.save()
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.clipPath(path)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + topHighlightHeight, specularPaint)
            canvas.restore()
        } else {
            canvas.save()
            path.reset()
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.clipPath(path)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + topHighlightHeight, specularPaint)
            canvas.restore()
        }

        // 3. Press Glow / Internal Liquid Bloom
        if (isPressed) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val maxR = Math.max(rect.width(), rect.height()) * 0.6f
            val pressBloomAlpha = (110 * clampedIntensity).toInt().coerceIn(0, 255)

            val glowShader = RadialGradient(
                cx,
                cy,
                maxR,
                Color.argb(pressBloomAlpha, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            pressGlowPaint.shader = glowShader
            if (isCircle) {
                canvas.drawCircle(cx, cy, Math.min(rect.width(), rect.height()) * 0.5f, pressGlowPaint)
            } else {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, pressGlowPaint)
            }
        }

        // 4. Refraction Glass Border (Dual Gradient Rim Lighting)
        val strokeWidth = (1.1f * density.coerceIn(1f, 2.5f)).coerceIn(1f, 3.5f)
        strokePaint.strokeWidth = strokeWidth

        val topRimAlpha = ((if (isPressed) 210 else 170) * clampedIntensity).toInt().coerceIn(0, 255)
        val midRimAlpha = ((if (isPressed) 100 else 65) * clampedIntensity).toInt().coerceIn(0, 255)
        val botRimAlpha = ((if (isPressed) 140 else 90) * clampedIntensity).toInt().coerceIn(0, 255)

        val rimShader = LinearGradient(
            rect.left,
            rect.top,
            rect.left,
            rect.bottom,
            intArrayOf(
                Color.argb(topRimAlpha, 255, 255, 255),
                Color.argb(midRimAlpha, 255, 255, 255),
                Color.argb(botRimAlpha, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = rimShader

        val inset = strokeWidth * 0.5f
        val strokeRect = RectF(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset
        )
        val strokeRadius = Math.max(0f, cornerRadius - inset)

        if (isCircle) {
            val cx = strokeRect.centerX()
            val cy = strokeRect.centerY()
            val r = Math.min(strokeRect.width(), strokeRect.height()) * 0.5f
            canvas.drawCircle(cx, cy, r, strokePaint)
        } else {
            canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, strokePaint)
        }
    }

    /**
     * Applies Liquid Glass background styling to a standard button / View (toolbar keys, etc.)
     */
    fun applyLiquidGlassToView(view: View, baseColor: Int, cornerRadiusPx: Float, intensity: Float = 0.75f) {
        val clampedIntensity = intensity.coerceIn(0.1f, 1.0f)
        val strokeColor = Color.argb((160 * clampedIntensity).toInt(), 255, 255, 255)
        val strokeWidth = (view.resources.displayMetrics.density * 1.2f).toInt().coerceAtLeast(1)

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(baseColor)
            setStroke(strokeWidth, strokeColor)
        }
        view.background = drawable
    }
}
