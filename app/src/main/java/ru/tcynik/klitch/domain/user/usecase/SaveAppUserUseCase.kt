package ru.tcynik.klitch.domain.user.usecase

import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.repository.AppUserRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

class SaveAppUserUseCase(
    private val repository: AppUserRepository,
) : UseCase<AppUser, Unit>() {
    override suspend fun invoke(params: AppUser) =
        repository.saveUser(params)
}
