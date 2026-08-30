// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.max
import kotlin.math.min

class FloatingKeyboardManager {

    companion object {
        const val PREF_FLOATING_KEYBOARD_PREFIX = "floating_keyboard_enabled"
        const val PREF_FLOATING_SCALE_PREFIX = "floating_keyboard_scale"
        const val PREF_FLOATING_X_PREFIX = "floating_keyboard_x"
        const val PREF_FLOATING_Y_PREFIX = "floating_keyboard_y"

        fun isFloatingModeEnabled(context: Context): Boolean {
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            return Settings.readFloatingKeyboardEnabled(context.prefs(), isLandscape, FoldableUtils.isFolded)
        }

        fun setFloatingModeEnabled(context: Context, enabled: Boolean) {
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            Settings.writeFloatingKeyboardEnabled(context.prefs(), enabled, isLandscape, FoldableUtils.isFolded)
        }

        fun getFloatingScale(context: Context): Float {
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            return Settings.readFloatingKeyboardScale(context.prefs(), isLandscape, FoldableUtils.isFolded)
        }

        fun setFloatingScale(context: Context, scale: Float) {
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            Settings.writeFloatingKeyboardScale(context.prefs(), scale, isLandscape, FoldableUtils.isFolded)
        }

        fun getFloatingPosition(
            context: Context,
            outPos: IntArray,
            screenWidth: Int,
            screenHeight: Int,
            keyboardWidth: Int,
            keyboardHeight: Int
        ) {
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val prefs = context.prefs()
            val savedX = Settings.readFloatingKeyboardX(prefs, isLandscape, FoldableUtils.isFolded)
            val savedY = Settings.readFloatingKeyboardY(prefs, isLandscape, FoldableUtils.isFolded)

            val maxX = max(0, screenWidth - keyboardWidth)
            val maxY = max(0, screenHeight - keyboardHeight)

            val finalX = if (savedX < 0) {
                (screenWidth - keyboardWidth) / 2
            } else {
                savedX.coerceIn(0, maxX)
            }

            val finalY = if (savedY < 0) {
                // Default to ~65% down the screen
                (screenHeight * 0.65f).toInt().coerceIn(0, maxY)
            } else {
                savedY.coerceIn(0, maxY)
            }

            outPos[0] = finalX
            outPos[1] = finalY
        }

        fun saveFloatingPosition(context: Context, x: Int, y: Int) {
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            Settings.writeFloatingKeyboardPosition(context.prefs(), x, y, isLandscape, FoldableUtils.isFolded)
        }

        fun applyFloatingWindowLayout(service: InputMethodService, inputView: View?) {
            val window = service.window?.window ?: return
            val lp = window.attributes ?: return
            val floating = isFloatingModeEnabled(service)

            val mainFrame = inputView?.findViewById<RoundedKeyboardFrameView>(R.id.main_keyboard_frame)
            mainFrame?.isFloatingMode = floating

            val controlBar = inputView?.findViewById<View>(R.id.floating_control_bar)
            controlBar?.visibility = if (floating) View.VISIBLE else View.GONE

            val metrics = service.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels

            if (floating) {
                window.setGravity(Gravity.TOP or Gravity.START)
                lp.gravity = Gravity.TOP or Gravity.START

                val scale = getFloatingScale(service)
                val isLandscape = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val baseScale = if (isLandscape) 0.58f else 0.84f
                val effectiveScale = (baseScale * scale).coerceIn(0.45f, 0.96f)
                val kbWidth = (screenW * effectiveScale).toInt().coerceIn(min(screenW, (screenW * 0.40f).toInt()), screenW)

                val estimatedHeight = inputView?.measuredHeight.takeIf { it != null && it > 0 }
                    ?: (screenH * 0.35f).toInt()
                val pos = IntArray(2)
                getFloatingPosition(service, pos, screenW, screenH, kbWidth, estimatedHeight)

                lp.x = pos[0]
                lp.y = pos[1]
                lp.width = kbWidth
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                window.attributes = lp
            } else {
                window.setGravity(Gravity.BOTTOM)
                lp.gravity = Gravity.BOTTOM
                lp.x = 0
                lp.y = 0
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                window.attributes = lp
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun setupFloatingControlBar(service: LatinIME, mainKeyboardFrame: View) {
            val controlBar = mainKeyboardFrame.findViewById<View>(R.id.floating_control_bar) ?: return
            val btnDock = controlBar.findViewById<ImageButton>(R.id.btn_floating_dock)
            val dragContainer = controlBar.findViewById<View>(R.id.floating_drag_handle_container)

            val colors = Settings.getValues()?.mColors
            if (colors != null) {
                btnDock?.let { colors.setColor(it, ColorType.ONE_HANDED_MODE_BUTTON) }
            }

            btnDock?.setOnClickListener {
                setFloatingModeEnabled(service, false)
                KeyboardSwitcher.getInstance().setFloatingModeEnabled(false)
            }

            var startTouchX = 0f
            var startTouchY = 0f
            var startWindowX = 0
            var startWindowY = 0
            var isDragging = false
            var lastTapTime = 0L

            val scaleGestureDetector = android.view.ScaleGestureDetector(
                service,
                object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                        val currentScale = getFloatingScale(service)
                        val newScale = (currentScale * detector.scaleFactor).coerceIn(0.65f, 1.35f)
                        setFloatingScale(service, newScale)
                        applyFloatingWindowLayout(service, service.window?.window?.decorView?.findViewById(R.id.main_keyboard_frame))
                        return true
                    }
                }
            )

            dragContainer?.setOnTouchListener { _, event ->
                scaleGestureDetector.onTouchEvent(event)
                if (scaleGestureDetector.isInProgress) {
                    isDragging = false
                    return@setOnTouchListener true
                }

                val window = service.window?.window ?: return@setOnTouchListener false
                val lp = window.attributes ?: return@setOnTouchListener false
                val metrics = service.resources.displayMetrics
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                val kbW = lp.width
                val kbH = mainKeyboardFrame.height.takeIf { it > 0 } ?: (screenH * 0.35f).toInt()
                val maxX = max(0, screenW - kbW)
                val maxY = max(0, screenH - kbH)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            // Double tap: toggle scale preset (e.g. 0.85 -> 1.0 -> 1.2 -> 0.85)
                            val currentScale = getFloatingScale(service)
                            val nextScale = when {
                                currentScale < 0.9f -> 1.0f
                                currentScale < 1.15f -> 1.22f
                                else -> 0.85f
                            }
                            setFloatingScale(service, nextScale)
                            KeyboardSwitcher.getInstance().reloadKeyboard()
                            lastTapTime = 0L
                            return@setOnTouchListener true
                        }
                        lastTapTime = now

                        startTouchX = event.rawX
                        startTouchY = event.rawY
                        startWindowX = lp.x
                        startWindowY = lp.y
                        isDragging = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDragging) return@setOnTouchListener true
                        val dx = (event.rawX - startTouchX).toInt()
                        val dy = (event.rawY - startTouchY).toInt()

                        val newX = (startWindowX + dx).coerceIn(0, maxX)
                        val newY = (startWindowY + dy).coerceIn(0, maxY)

                        lp.x = newX
                        lp.y = newY
                        window.attributes = lp
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            isDragging = false
                            saveFloatingPosition(service, lp.x, lp.y)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }
}
