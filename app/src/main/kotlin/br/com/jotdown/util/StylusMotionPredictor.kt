package br.com.jotdown.util

import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.input.motionprediction.MotionEventPredictor
import kotlin.math.hypot

val LocalStylusPredictor = staticCompositionLocalOf<StylusMotionPredictor?> { null }

/**
 * Wraps AndroidX [MotionEventPredictor] (Apache-2.0) to track stylus velocity and expose
 * predicted window-coordinate points for low-latency live ink rendering.
 */
class StylusMotionPredictor(view: View) {

    private val predictor: MotionEventPredictor = MotionEventPredictor.newInstance(view)

    @Volatile
    var latestPredictedWindowPoints: List<Pair<Float, Float>> = emptyList()
        private set

    fun onTouchEvent(ev: MotionEvent) {
        if (ev.pointerCount == 0 || ev.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            latestPredictedWindowPoints = emptyList()
            return
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                predictor.record(ev)
                latestPredictedWindowPoints = emptyList()
            }
            MotionEvent.ACTION_MOVE -> {
                predictor.record(ev)
                val pred = predictor.predict()
                try {
                    if (pred != null && pred.pointerCount > 0) {
                        val pts = ArrayList<Pair<Float, Float>>(4)
                        val hist = pred.historySize
                        for (i in 0 until hist) {
                            val px = pred.getHistoricalX(i)
                            val py = pred.getHistoricalY(i)
                            if (hypot((px - ev.x).toDouble(), (py - ev.y).toDouble()) <= 120.0) {
                                pts.add(px to py)
                            }
                        }
                        val px = pred.x
                        val py = pred.y
                        if (hypot((px - ev.x).toDouble(), (py - ev.y).toDouble()) <= 120.0) {
                            pts.add(px to py)
                        }
                        latestPredictedWindowPoints = pts.take(4)
                    } else {
                        latestPredictedWindowPoints = emptyList()
                    }
                } finally {
                    pred?.recycle()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                latestPredictedWindowPoints = emptyList()
            }
        }
    }
}
