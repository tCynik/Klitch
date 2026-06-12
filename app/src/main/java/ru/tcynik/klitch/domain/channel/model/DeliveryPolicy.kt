package ru.tcynik.klitch.domain.channel.model

enum class DeliveryPolicy {
    /** Показать в UI, уведомить, сохранить */
    DELIVER,
    /** Сохранить без уведомлений (Emergency chat вне SOS) */
    SILENT_STORE,
    /** Не сохранять, не показывать */
    DROP,
}

enum class InboundPacketKind {
    TEXT_MESSAGE,
    POSITION,
    WAYPOINT,
    TELEMETRY,
    NODEINFO,
    ALERT,
}
