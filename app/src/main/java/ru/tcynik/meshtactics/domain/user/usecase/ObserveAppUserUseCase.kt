package ru.tcynik.meshtactics.domain.user.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveAppUserUseCase(
    private val repository: AppUserRepository,
) : FlowUseCase<NoParams, AppUser>() {
    override fun invoke(params: NoParams): Flow<AppUser> =
        repository.observeUser()
}
