package com.example.success

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun TimeSeriesPlot(
    data: List<AccelerometerData>,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            if (data.isEmpty()) return@Canvas

            val values = when (label) {
                "ax" -> data.map { it.ax }
                "ay" -> data.map { it.ay }
                "az" -> data.map { it.az }
                else -> return@Canvas
            }

            val minVal = values.minOrNull() ?: 0f
            val maxVal = values.maxOrNull() ?: 0f
            val range = maxVal - minVal
            val width = size.width
            val height = size.height
            val path = Path()
            val points = values.mapIndexed { index, value ->
                val x = (index.toFloat() / (values.size - 1)) * width
                val y = if (range == 0f) {
                    height / 2f
                } else {
                    height - ((value - minVal) / range) * height
                }
                Offset(x, y)
            }

            points.forEachIndexed { index, point ->
                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }
            }

            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
    }
}