package ru.tcynik.klitch.data.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

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
                // When multiple slots share the same hash (e.g. LongFast on slot 0 and 1),
                // the lowest slot index wins — Primary (0) takes priority over Emergency (1).
                val hashToSlot = buildMap<ContourHash, Int> {
                    slotToHash.entries
                        .sortedBy { it.key }
                        .forEach { (slot, hash) -> if (!containsKey(hash)) put(hash, slot) }
                }
                _mapsFlow.value = ChannelSlotMaps(
                    slotToHash = slotToHash,
                    hashToSlot = hashToSlot,
                )
            }
            .launchIn(scope)
    }
}
