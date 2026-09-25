package com.kairosera.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kairosera.R

/**
 * The Kairos Era mark: the brand artwork (sun, mountains and path in a gold ring), cut from the
 * official logo with a see-through edge so it sits on any background. Decorative unless a
 * [description] is given.
 */
@Composable
fun KairosLogo(modifier: Modifier = Modifier, description: String? = null) {
    Image(
        painterResource(R.drawable.kairos_emblem),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/** The "KAIROS ERA" wordmark from the official logo (cream and gold serif). */
@Composable
fun KairosWordmark(modifier: Modifier = Modifier, description: String? = null) {
    Image(
        painterResource(R.drawable.kairos_wordmark),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
