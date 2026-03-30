package ru.tcynik.mymesh1.domain.usecase

import kotlinx.coroutines.test.runTest
import ru.tcynik.mymesh1.domain.usecase.base.NoParams
import ru.tcynik.mymesh1.domain.usecase.base.UseCase
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
