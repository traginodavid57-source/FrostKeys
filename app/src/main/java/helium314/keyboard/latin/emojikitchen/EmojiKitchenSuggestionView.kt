// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.emojikitchen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil.load
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.dpToPx

@SuppressLint("ViewConstructor")
class EmojiKitchenSuggestionView(
    context: Context,
    private val latinIME: LatinIME,
    val emojis: List<String>,
    val combos: List<EmojiKitchenCombo>,
    val charsCountToDelete: Int
) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val colors = Settings.getValues().mColors

        // Close button (X)
        val closeButton = ImageButton(context).apply {
            layoutParams = LayoutParams(38.dpToPx(resources), LayoutParams.MATCH_PARENT).apply {
                marginStart = 2.dpToPx(resources)
                marginEnd = 2.dpToPx(resources)
            }
            setImageResource(R.drawable.ic_close)
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundResource(android.R.drawable.list_selector_background)
            colors.setColor(this, ColorType.KEY_TEXT)
            contentDescription = "Fechar"
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance()
                    .performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                latinIME.dismissEmojiKitchenSuggestion()
                helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().emojiPalettesView?.dismissEmojiKitchen()
            }
        }
        addView(closeButton)

        // Scroll view for stickers
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val stripContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(2.dpToPx(resources), 2.dpToPx(resources), 4.dpToPx(resources), 2.dpToPx(resources))
        }

        val itemSize = 44.dpToPx(resources)
        val itemMargin = 3.dpToPx(resources)

        for ((index, combo) in combos.withIndex()) {
            val isDirectMix = emojis.size >= 2 && index == 0
            val itemContainer = FrameLayout(context).apply {
                layoutParams = LayoutParams(itemSize, itemSize).apply {
                    setMargins(itemMargin, 0, itemMargin, 0)
                }
                isClickable = true
                isFocusable = true

                val shape = GradientDrawable().apply {
                    cornerRadius = 8.dpToPx(resources).toFloat()
                    val isDark = ColorUtils.calculateLuminance(colors.get(ColorType.MAIN_BACKGROUND)) < 0.5
                    if (isDirectMix) {
                        setColor(if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(45, 0, 0, 0))
                        setStroke(2.dpToPx(resources), colors.get(ColorType.ACTION_KEY_BACKGROUND))
                    } else {
                        setColor(if (isDark) Color.argb(35, 255, 255, 255) else Color.argb(25, 0, 0, 0))
                        setStroke(1.dpToPx(resources), if (isDark) Color.argb(55, 255, 255, 255) else Color.argb(35, 0, 0, 0))
                    }
                }
                background = shape
            }

            val imageView = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    val p = 2.dpToPx(resources)
                    setMargins(p, p, p, p)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                load(combo.url) {
                    crossfade(true)
                }
            }
            itemContainer.addView(imageView)

            var isSubmitting = false
            itemContainer.setOnClickListener { v ->
                if (isSubmitting) return@setOnClickListener
                isSubmitting = true
                AudioAndHapticFeedbackManager.getInstance()
                    .performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, v, HapticEvent.KEY_PRESS)
                v.alpha = 0.5f

                EmojiKitchenHelper.commitSticker(
                    context = context,
                    latinIME = latinIME,
                    combo = combo,
                    charsCountToDelete = charsCountToDelete
                ) { success ->
                    if (!success) {
                        v.alpha = 1.0f
                        isSubmitting = false
                    }
                }
            }

            stripContainer.addView(itemContainer)
        }

        scrollView.addView(stripContainer)
        addView(scrollView)
    }
}
