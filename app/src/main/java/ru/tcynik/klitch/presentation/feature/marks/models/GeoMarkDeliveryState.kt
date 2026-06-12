package ru.tcynik.klitch.presentation.feature.marks.models

enum class GeoMarkDeliveryState {
    /** Только локальное хранилище (адресат «Хранилище»), без отправки в mesh. */
    LOCAL,
    /** В БД на этом устройстве, ожидает передачи в mesh (очередь радио). */
    QUEUED,
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
    isSelf && authorNodeId.isEmpty() && logicalChannelId.isNotEmpty() -> GeoMarkDeliveryState.QUEUED
    else -> GeoMarkDeliveryState.SENT
}
