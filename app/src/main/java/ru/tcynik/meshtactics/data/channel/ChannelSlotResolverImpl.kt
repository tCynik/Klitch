package ru.tcynik.meshtactics.data.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ChannelSlotResolverImpl(
    observeNodeChannels: ObserveNodeChannelsUseCase,
) : ChannelSlotResolver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _mapsFlow = MutableStateFlow(ChannelSlotMaps())
    override val mapsFlow: StateFlow<ChannelSlotMaps> = _mapsFlow.asStateFlow()

    override val slotToHash: Map<Int, ContourHash> get() = _mapsFlow.value.slotToHash
    override val hashToSlot: Map<ContourHash, Int> get() = _mapsFlow.value.hashToSlot

    init {
        observeNodeChannels(NoParams)
            .onEach { slots ->
                val slotToHash = slots
                    .filter { it.isEnabled }
                    .associate { slot -> slot.index to ContourHash.compute(slot.name, slot.psk) }
                _mapsFlow.value = ChannelSlotMaps(
                    slotToHash = slotToHash,
                    hashToSlot = slotToHash.entries.associate { (k, v) -> v to k },
                )
            }
            .launchIn(scope)
    }
}
