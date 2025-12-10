package com.example.trackr.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

/**
 * Decorator to add colored dots/bubbles under calendar dates
 */
class EventDecorator(
    private val color: Int,
    private val dates: Collection<CalendarDay>
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return dates.contains(day)
    }

    override fun decorate(view: DayViewFacade) {
        view.addSpan(DotSpan(8f, color))
    }
}

/**
 * Custom span to draw a dot under the date
 */
class DotSpan(
    private val radius: Float,
    private val color: Int
) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        val oldColor = paint.color
        paint.color = color

        // Draw dot below the date number
        val centerX = (left + right) / 2f
        val centerY = bottom + radius + 10f // Position below text

        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.color = oldColor
    }
}