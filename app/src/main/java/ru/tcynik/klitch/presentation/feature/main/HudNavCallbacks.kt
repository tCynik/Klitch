package ru.tcynik.klitch.presentation.feature.main

// Navigation and action callbacks used by HUD state builders.
// Provided by NavGraph (has navController + all VM access) via MainViewModel.provideNavCallbacks().
data class HudNavCallbacks(
    // Navigation
    val onRadioClick: () -> Unit = {},
    val onMeshClick: () -> Unit = {},
    val onChatClick: () -> Unit = {},
    val onMainSettingsClick: () -> Unit = {},
    val onMapSettingsClick: () -> Unit = {},
    val onDisplaySettingsClick: () -> Unit = {},
    val onUserSettingsClick: () -> Unit = {},
    val onGeoMarksList: () -> Unit = {},
    val onExitApp: () -> Unit = {},
    // VM actions wired into HUD buttons
    val onToggleMenuDrawer: () -> Unit = {},
    val onFollowMeToggle: () -> Unit = {},
    val onCompassTap: () -> Unit = {},
    val onToggleMarkTool: () -> Unit = {},
    val onToggleGeoMarksSheet: () -> Unit = {},
    val onToggleTrackRecordingSheet: () -> Unit = {},
    val onSosClick: () -> Unit = {},
)
