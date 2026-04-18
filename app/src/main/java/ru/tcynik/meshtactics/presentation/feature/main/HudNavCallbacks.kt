package ru.tcynik.meshtactics.presentation.feature.main

// Navigation callbacks for HUD right column buttons.
// Provided by NavGraph (has navController access) via MainViewModel.provideNavCallbacks().
data class HudNavCallbacks(
    val onRadioClick: () -> Unit = {},
    val onSettingsClick: () -> Unit = {},
    val onMeshClick: () -> Unit = {},
    val onChatClick: () -> Unit = {},
)
