package br.com.jotdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.ui.theme.Indigo600
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToLibrary: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onNavigateToLibrary()
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Indigo600), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Jotdown", fontWeight = FontWeight.Black, fontSize = 32.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}