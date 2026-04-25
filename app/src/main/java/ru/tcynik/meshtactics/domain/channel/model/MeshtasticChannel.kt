package ru.tcynik.meshtactics.domain.channel.model

data class MeshtasticChannel(
    val psk: String,
    val channelHash: ContourHash,
)
