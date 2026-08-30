// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.max
import kotlin.math.min

class FloatingKeyboardManager {

    companion object {
        const val PREF_FLOATING_KEYBOARD_PREFIX = "floating_keyboard_enabled"
        const val PREF_FLOATING_SCALE_PREFIX = "floating_keyboard_scale"
        const val PREF_FLOATING_X_PREFIX = "floating_keyboard_x"
        const val PREF_FLOATING_Y_PREFIX = "floating_keyboard_y"

        private const val DEFAULT_BOTTOM_MARGIN_DP = 40f
        private const val MIN_TOP_MARGIN_DP = 48f
        private const val MIN_SIDE_MARGIN_DP = 8f
        private const val MIN_BOTTOM_MARGIN_DP = 24f

        private var lastTapTime = 0L

        private fun dpToPx(context: Context, dp: Float): Int {
            return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        }

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

            val sideMargin = dpToPx(context, MIN_SIDE_MARGIN_DP)
            val topMargin = dpToPx(context, MIN_TOP_MARGIN_DP)
            val bottomMargin = dpToPx(context, MIN_BOTTOM_MARGIN_DP)
            val defaultBottomMargin = dpToPx(context, DEFAULT_BOTTOM_MARGIN_DP)

            val minX = sideMargin
            val maxX = max(minX, screenWidth - keyboardWidth - sideMargin)
            val minY = topMargin
            val maxY = max(minY, screenHeight - keyboardHeight - bottomMargin)

            val finalX = if (savedX < 0) {
                ((screenWidth - keyboardWidth) / 2).coerceIn(minX, maxX)
            } else {
                savedX.coerceIn(minX, maxX)
            }

            val finalY = if (savedY < 0) {
                // Default position: floating comfortably above the bottom of the screen with a clean margin
                (screenHeight - keyboardHeight - defaultBottomMargin).coerceIn(minY, maxY)
            } else {
                savedY.coerceIn(minY, maxY)
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

                val estimatedHeight = mainFrame?.height?.takeIf { it > 0 }
                    ?: inputView?.measuredHeight?.takeIf { it > 0 }
                    ?: (screenH * 0.35f).toInt()

                val pos = IntArray(2)
                getFloatingPosition(service, pos, screenW, screenH, kbWidth, estimatedHeight)

                lp.x = pos[0]
                lp.y = pos[1]
                lp.width = kbWidth
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                window.attributes = lp

                // Ensure position is adjusted once the view is measured and real height is known
                mainFrame?.post {
                    if (!isFloatingModeEnabled(service)) return@post
                    val realH = mainFrame.height
                    if (realH > 0) {
                        val sideMargin = dpToPx(service, MIN_SIDE_MARGIN_DP)
                        val topMargin = dpToPx(service, MIN_TOP_MARGIN_DP)
                        val bottomMargin = dpToPx(service, MIN_BOTTOM_MARGIN_DP)
                        val currentX = lp.x
                        val currentY = lp.y
                        val maxX = max(sideMargin, screenW - kbWidth - sideMargin)
                        val maxY = max(topMargin, screenH - realH - bottomMargin)
                        val clampedX = currentX.coerceIn(sideMargin, maxX)
                        val clampedY = currentY.coerceIn(topMargin, maxY)
                        if (clampedX != currentX || clampedY != currentY) {
                            lp.x = clampedX
                            lp.y = clampedY
                            window.attributes = lp
                            saveFloatingPosition(service, clampedX, clampedY)
                        }
                    }
                }
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
            val dragHandlePill = controlBar.findViewById<View>(R.id.floating_drag_handle_pill)

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
            dragContainer?.setOnTouchListener { v, event ->
                val window = service.window?.window ?: return@setOnTouchListener false
                val lp = window.attributes ?: return@setOnTouchListener false
                val metrics = service.resources.displayMetrics
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                val kbW = lp.width
                val kbH = mainKeyboardFrame.height.takeIf { it > 0 } ?: (screenH * 0.35f).toInt()

                val sideMargin = dpToPx(service, MIN_SIDE_MARGIN_DP)
                val topMargin = dpToPx(service, MIN_TOP_MARGIN_DP)
                val bottomMargin = dpToPx(service, MIN_BOTTOM_MARGIN_DP)

                val minX = sideMargin
                val maxX = max(minX, screenW - kbW - sideMargin)
                val minY = topMargin
                val maxY = max(minY, screenH - kbH - bottomMargin)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        mainKeyboardFrame.parent?.requestDisallowInterceptTouchEvent(true)

                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            // Double tap: toggle scale preset (e.g. 0.85 -> 1.0 -> 1.22 -> 0.85)
                            val currentScale = getFloatingScale(service)
                            val nextScale = when {
                                currentScale < 0.92f -> 1.0f
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

                        // Smooth visual feedback on touch down
                        dragHandlePill?.animate()?.scaleX(1.15f)?.scaleY(1.25f)?.alpha(0.85f)?.setDuration(120)?.start()
                        runCatching {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDragging) return@setOnTouchListener true
                        v.parent?.requestDisallowInterceptTouchEvent(true)

                        val dx = (event.rawX - startTouchX).toInt()
                        val dy = (event.rawY - startTouchY).toInt()

                        val newX = (startWindowX + dx).coerceIn(minX, maxX)
                        val newY = (startWindowY + dy).coerceIn(minY, maxY)

                        if (newX != lp.x || newY != lp.y) {
                            lp.x = newX
                            lp.y = newY
                            window.attributes = lp
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            isDragging = false
                            dragHandlePill?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.alpha(1.0f)?.setDuration(150)?.start()
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
