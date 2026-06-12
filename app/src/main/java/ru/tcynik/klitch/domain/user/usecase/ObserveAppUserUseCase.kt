package ru.tcynik.klitch.domain.user.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.repository.AppUserRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveAppUserUseCase(
    private val repository: AppUserRepository,
) : FlowUseCase<NoParams, AppUser>() {
    override fun invoke(params: NoParams): Flow<AppUser> =
        repository.observeUser()
}
