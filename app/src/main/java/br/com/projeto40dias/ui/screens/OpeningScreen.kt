package br.com.projeto40dias.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import br.com.projeto40dias.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tela de abertura: a arte do Projeto 40 Dias com entrada suave (fade + leve zoom).
 * Fica ~1,8 s; um toque pula. O app já carrega por baixo enquanto ela aparece.
 */
@Composable
fun OpeningScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.94f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        scale.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        delay(400)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_art),
            contentDescription = "Projeto 40 Dias — developed by Kauê Sanchez",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value; scaleY = scale.value
            },
        )
    }
}
