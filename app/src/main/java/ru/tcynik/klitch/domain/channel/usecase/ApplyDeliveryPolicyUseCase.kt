package ru.tcynik.klitch.domain.channel.usecase

import ru.tcynik.klitch.domain.channel.model.ContourResolution
import ru.tcynik.klitch.domain.channel.model.DeliveryPolicy
import ru.tcynik.klitch.domain.channel.model.InboundPacketKind

class ApplyDeliveryPolicyUseCase {
    operator fun invoke(
        resolution: ContourResolution,
        packetKind: InboundPacketKind,
    ): DeliveryPolicy = when (packetKind) {
        InboundPacketKind.TEXT_MESSAGE -> when (resolution) {
            is ContourResolution.Deliver -> DeliveryPolicy.DELIVER
            is ContourResolution.SilentStore -> DeliveryPolicy.SILENT_STORE
            is ContourResolution.Drop -> DeliveryPolicy.DROP
        }
        InboundPacketKind.WAYPOINT,
        InboundPacketKind.POSITION,
        InboundPacketKind.TELEMETRY,
        InboundPacketKind.NODEINFO,
        -> when (resolution) {
            is ContourResolution.Deliver -> DeliveryPolicy.DELIVER
            else -> DeliveryPolicy.DROP
        }
        InboundPacketKind.ALERT -> when (resolution) {
            is ContourResolution.Deliver -> DeliveryPolicy.DELIVER
            is ContourResolution.SilentStore -> DeliveryPolicy.SILENT_STORE
            is ContourResolution.Drop -> DeliveryPolicy.DROP
        }
    }
}
