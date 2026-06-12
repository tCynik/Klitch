package ru.tcynik.klitch.domain.usecase

import kotlinx.coroutines.test.runTest
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.domain.usecase.base.UseCase
import kotlin.test.Test
import kotlin.test.assertEquals

class UseCaseTest {

    private val useCase = object : UseCase<NoParams, String>() {
        override suspend fun invoke(params: NoParams) = "ok"
    }

    @Test
    fun `invoke returns expected result`() = runTest {
        assertEquals("ok", useCase(NoParams))
    }
}
