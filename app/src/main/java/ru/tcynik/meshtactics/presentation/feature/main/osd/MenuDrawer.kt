package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.layouts.MenuDrawerItem
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MenuDrawerUiState

// TODO: implement landscape variant when landscape HUD is refactored
@Composable
fun MenuDrawer(state: MenuDrawerUiState) {
    BackHandler(enabled = state.isOpen, onBack = state.onDismiss)

    AnimatedVisibility(
        visible = state.isOpen,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = state.onDismiss,
                )
        )
    }

    AnimatedVisibility(
        visible = state.isOpen,
        enter = slideInHorizontally(animationSpec = tween(250)) { -it },
        exit = slideOutHorizontally(animationSpec = tween(250)) { -it },
    ) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            state.items.forEachIndexed { index, item ->
                MenuDrawerItem(item)
                if (index < state.items.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}
