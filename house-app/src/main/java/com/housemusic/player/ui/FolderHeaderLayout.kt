package com.housemusic.player.ui

import android.content.Context
import android.util.AttributeSet
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.housemusic.player.R
import kotlin.math.ceil
import kotlin.math.max

/** Center a fitting name in the button gap; give a longer name its own left-aligned row. */
class FolderHeaderLayout(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private var overflow = false
    private var buttonHeight = 0
    private val gap get() = (6 * resources.displayMetrics.density).toInt()
    // AppCompat's button background has a 4dp horizontal inset.
    private val buttonInset get() = (4 * resources.displayMetrics.density).toInt()

    override fun onFinishInflate() {
        super.onFinishInflate()
        findViewById<TextView>(R.id.pathText).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // A single-line TextView may reuse its old measured size when only its
                // text changes. The parent must reconsider whether the name still fits.
                requestLayout()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val start = findViewById<android.view.View>(R.id.settingsButton)
        val end = findViewById<android.view.View>(R.id.upButton)
        val label = findViewById<TextView>(R.id.pathText)
        measureChild(start, widthMeasureSpec, heightMeasureSpec)
        measureChild(end, widthMeasureSpec, heightMeasureSpec)
        buttonHeight = max(start.measuredHeight, end.measuredHeight)
        val space = (width - paddingLeft - paddingRight - start.measuredWidth - end.measuredWidth - 2 * gap).coerceAtLeast(0)
        val textWidth = ceil(label.paint.measureText(label.text.toString())).toInt() + label.compoundPaddingLeft + label.compoundPaddingRight
        overflow = textWidth > space
        label.gravity = if (overflow) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        val labelWidth = if (overflow) width - paddingLeft - paddingRight - 2 * buttonInset else space
        label.measure(MeasureSpec.makeMeasureSpec(labelWidth.coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val contentHeight = if (overflow) buttonHeight + gap + label.measuredHeight else max(buttonHeight, label.measuredHeight)
        setMeasuredDimension(width, resolveSize(paddingTop + contentHeight + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val start = findViewById<android.view.View>(R.id.settingsButton)
        val end = findViewById<android.view.View>(R.id.upButton)
        val label = findViewById<TextView>(R.id.pathText)
        start.layout(paddingLeft, paddingTop, paddingLeft + start.measuredWidth, paddingTop + start.measuredHeight)
        end.layout(width - paddingRight - end.measuredWidth, paddingTop, width - paddingRight, paddingTop + end.measuredHeight)
        val x = if (overflow) paddingLeft + buttonInset else start.right + gap
        val y = if (overflow) paddingTop + buttonHeight + gap else paddingTop + (buttonHeight - label.measuredHeight) / 2
        label.layout(x, y, x + label.measuredWidth, y + label.measuredHeight)
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet) = LayoutParams(context, attrs)
}
