package ru.tcynik.mymesh1.domain.usecase.base

/**
 * Базовый use case для однократного suspend-вызова.
 * Params = NoParams если параметры не нужны.
 */
abstract class UseCase<in Params, out Result> {
    abstract suspend operator fun invoke(params: Params): Result
}

/** Заглушка для use case'ов без параметров */
object NoParams
