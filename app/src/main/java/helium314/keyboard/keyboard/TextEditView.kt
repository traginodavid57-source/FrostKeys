// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils

class TextEditView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var keyboardActionListener: KeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER
    private val handler = Handler(Looper.getMainLooper())
    private val keyViews = mutableListOf<View>()
    private val iconViews = mutableListOf<ImageView>()

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.text_edit_view, this, true)
        setupActions()
    }

    fun setKeyboardActionListener(listener: KeyboardActionListener) {
        keyboardActionListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val settings = Settings.getValues()
        val abcHeight = ResourceUtils.getKeyboardHeight(resources, settings)
        val finalHeight = abcHeight + paddingTop + paddingBottom
        val constrainedHeightSpec = MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, constrainedHeightSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), finalHeight)
    }

    fun onOpen() {
        val colors = Settings.getValues()?.mColors
        if (colors != null) {
            updateThemeColors(colors)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupActions() {
        // Header actions
        findViewById<ImageButton>(R.id.text_edit_btn_close)?.setOnClickListener {
            performClickFeedback(it)
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
        }
        findViewById<ImageButton>(R.id.text_edit_btn_undo)?.setOnClickListener {
            sendCode(it, KeyCode.UNDO)
        }
        findViewById<ImageButton>(R.id.text_edit_btn_redo)?.setOnClickListener {
            sendCode(it, KeyCode.REDO)
        }
        findViewById<ImageButton>(R.id.text_edit_btn_paste_header)?.setOnClickListener {
            sendCode(it, KeyCode.CLIPBOARD_PASTE)
        }
        findViewById<ImageButton>(R.id.text_edit_btn_select_all_header)?.setOnClickListener {
            sendCode(it, KeyCode.CLIPBOARD_SELECT_ALL)
        }

        // Left column
        setupClickKey(R.id.text_edit_btn_select_word, R.id.text_edit_icon_select_word, KeyCode.CLIPBOARD_SELECT_WORD)
        setupClickKey(R.id.text_edit_btn_cut, R.id.text_edit_icon_cut, KeyCode.CLIPBOARD_CUT)
        setupClickKey(R.id.text_edit_btn_copy, R.id.text_edit_icon_copy, KeyCode.CLIPBOARD_COPY)
        setupClickKey(R.id.text_edit_btn_paste, R.id.text_edit_icon_paste, KeyCode.CLIPBOARD_PASTE)

        // Center column (D-Pad)
        setupClickKey(R.id.text_edit_btn_home, R.id.text_edit_icon_home, KeyCode.MOVE_START_OF_LINE)
        setupRepeatKey(R.id.text_edit_btn_up, R.id.text_edit_icon_up, KeyCode.ARROW_UP)
        setupClickKey(R.id.text_edit_btn_end, R.id.text_edit_icon_end, KeyCode.MOVE_END_OF_LINE)

        setupRepeatKey(R.id.text_edit_btn_left, R.id.text_edit_icon_left, KeyCode.ARROW_LEFT)
        setupClickKey(R.id.text_edit_btn_select_all, R.id.text_edit_icon_select_all, KeyCode.CLIPBOARD_SELECT_ALL)
        setupRepeatKey(R.id.text_edit_btn_right, R.id.text_edit_icon_right, KeyCode.ARROW_RIGHT)

        setupClickKey(R.id.text_edit_btn_page_up, R.id.text_edit_icon_page_up, KeyCode.MOVE_START_OF_PAGE)
        setupRepeatKey(R.id.text_edit_btn_down, R.id.text_edit_icon_down, KeyCode.ARROW_DOWN)
        setupClickKey(R.id.text_edit_btn_page_down, R.id.text_edit_icon_page_down, KeyCode.MOVE_END_OF_PAGE)

        // Right column
        setupClickKey(R.id.text_edit_btn_word_left, R.id.text_edit_icon_word_left, KeyCode.WORD_LEFT)
        setupClickKey(R.id.text_edit_btn_word_right, R.id.text_edit_icon_word_right, KeyCode.WORD_RIGHT)
        setupRepeatKey(R.id.text_edit_btn_delete, R.id.text_edit_icon_delete, KeyCode.DELETE)
        setupClickKey(R.id.text_edit_btn_enter, R.id.text_edit_icon_enter, Constants.CODE_ENTER)

        val title = findViewById<TextView>(R.id.text_edit_title)
        if (title != null) {
            KeyboardTypeface.applyToTextView(title)
        }
    }

    private fun setupClickKey(containerId: Int, iconId: Int, code: Int) {
        val container = findViewById<View>(containerId) ?: return
        val icon = findViewById<ImageView>(iconId)
        keyViews.add(container)
        if (icon != null) iconViews.add(icon)

        container.setOnClickListener {
            sendCode(it, code)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRepeatKey(containerId: Int, iconId: Int, code: Int) {
        val container = findViewById<View>(containerId) ?: return
        val icon = findViewById<ImageView>(iconId)
        keyViews.add(container)
        if (icon != null) iconViews.add(icon)

        var repeatRunnable: Runnable? = null
        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sendCode(v, code)
                    repeatRunnable = object : Runnable {
                        override fun run() {
                            sendCode(v, code)
                            handler.postDelayed(this, 60)
                        }
                    }
                    handler.postDelayed(repeatRunnable!!, 350)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatRunnable?.let { handler.removeCallbacks(it) }
                    repeatRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    private fun performClickFeedback(v: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
            KeyCode.NOT_SPECIFIED, v, HapticEvent.KEY_PRESS
        )
    }

    private fun sendCode(v: View, code: Int) {
        performClickFeedback(v)
        keyboardActionListener.onCodeInput(
            code,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        )
    }

    fun updateThemeColors(colors: Colors) {
        val keyboardTextColor = colors.get(ColorType.KEY_TEXT)
        val keyboardBgColor = colors.get(ColorType.MAIN_BACKGROUND)
        val keyBgColor = colors.get(ColorType.KEY_BACKGROUND)
        val headerBgColor = colors.get(ColorType.STRIP_BACKGROUND)

        // Root and header backgrounds
        setBackgroundColor(keyboardBgColor)
        findViewById<View>(R.id.text_edit_header)?.setBackgroundColor(headerBgColor)

        // Title and header buttons tint
        findViewById<TextView>(R.id.text_edit_title)?.setTextColor(keyboardTextColor)
        val headerButtons = listOf(
            R.id.text_edit_btn_close,
            R.id.text_edit_btn_undo,
            R.id.text_edit_btn_redo,
            R.id.text_edit_btn_paste_header,
            R.id.text_edit_btn_select_all_header
        )
        for (btnId in headerButtons) {
            findViewById<ImageButton>(btnId)?.setColorFilter(keyboardTextColor)
        }

        // Key containers background
        val cornerRadius = when (colors.themeStyle) {
            KeyboardTheme.STYLE_CIRCLE -> 1000f
            KeyboardTheme.STYLE_ROUNDED -> 16f * resources.displayMetrics.density
            else -> 8f * resources.displayMetrics.density
        }
        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(keyboardTextColor, 0x33))

        for (keyView in keyViews) {
            val shape = GradientDrawable().apply {
                this.cornerRadius = cornerRadius
                setColor(keyBgColor)
            }
            keyView.background = RippleDrawable(rippleColor, shape, null)
        }

        // Key icons tint
        for (icon in iconViews) {
            icon.setColorFilter(keyboardTextColor)
        }
    }
}
