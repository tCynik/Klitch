package ru.tcynik.klitch.presentation.util

private val MESHTASTIC_BLE_NAME_REGEX =
    Regex("^Meshtastic_([0-9a-fA-F]{4})$", RegexOption.IGNORE_CASE)
private val MESHTASTIC_DEFAULT_LONG_NAME_REGEX =
    Regex("^Meshtastic ([0-9a-fA-F]{4})$", RegexOption.IGNORE_CASE)

fun String.toMeshtasticDisplayShortName(): String {
    val trimmed = trim()
    for (regex in listOf(MESHTASTIC_BLE_NAME_REGEX, MESHTASTIC_DEFAULT_LONG_NAME_REGEX)) {
        regex.find(trimmed)?.groupValues?.get(1)?.lowercase()?.let { return it }
    }
    return trimmed
}
