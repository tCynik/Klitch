package ru.tcynik.meshtactics.presentation.feature.marks.models

enum class GeoMarkDeliveryState {
    /** Только локальное хранилище (адресат «Хранилище»), без отправки в mesh. */
    LOCAL,
    /** Создано на этом устройстве и отправлено в сеть. */
    SENT,
    /** Получено от другого узла. */
    RECEIVED,
}

fun resolveGeoMarkDeliveryState(
    isSelf: Boolean,
    logicalChannelId: String,
    authorNodeId: String,
): GeoMarkDeliveryState = when {
    !isSelf -> GeoMarkDeliveryState.RECEIVED
    logicalChannelId.isEmpty() && authorNodeId.isEmpty() -> GeoMarkDeliveryState.LOCAL
    else -> GeoMarkDeliveryState.SENT
}
