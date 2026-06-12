package ru.tcynik.klitch.domain.usecase.base

import kotlinx.coroutines.flow.Flow

/**
 * Базовый use case для реактивных потоков данных.
 */
abstract class FlowUseCase<in Params, out Result> {
    abstract operator fun invoke(params: Params): Flow<Result>
}
