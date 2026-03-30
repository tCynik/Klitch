package ru.tcynik.mymesh1.domain.usecase.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Use case оборачивающий результат в sealed Result<T>.
 * Позволяет единообразно обрабатывать Loading / Success / Error в ViewModel.
 */
abstract class ResultUseCase<in Params, out Result> {

    operator fun invoke(params: Params): Flow<UseCaseResult<Result>> = flow {
        emit(UseCaseResult.Loading)
        emit(UseCaseResult.Success(execute(params)))
    }.catch { e ->
        emit(UseCaseResult.Error(e))
    }

    protected abstract suspend fun execute(params: Params): Result
}

sealed class UseCaseResult<out T> {
    data object Loading : UseCaseResult<Nothing>()
    data class Success<T>(val data: T) : UseCaseResult<T>()
    data class Error(val throwable: Throwable) : UseCaseResult<Nothing>()
}
