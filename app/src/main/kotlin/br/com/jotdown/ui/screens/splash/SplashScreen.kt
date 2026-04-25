package br.com.jotdown.ui.screens.splash

import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1000,
                easing = { OvershootInterpolator(1.5f).getInterpolation(it) }
            )
        )
        delay(1200) // Tempo que a splash fica parada na tela
        onTimeout()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E1B4B)), // Indigo 900
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale.value)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF4F46E5)), // Indigo 600
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Jotdown",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.scale(scale.value)
            )
        }
    }
}
