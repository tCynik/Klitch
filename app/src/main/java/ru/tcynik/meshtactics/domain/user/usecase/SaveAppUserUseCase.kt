package ru.tcynik.meshtactics.domain.user.usecase

import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class SaveAppUserUseCase(
    private val repository: AppUserRepository,
) : UseCase<AppUser, Unit>() {
    override suspend fun invoke(params: AppUser) =
        repository.saveUser(params)
}
