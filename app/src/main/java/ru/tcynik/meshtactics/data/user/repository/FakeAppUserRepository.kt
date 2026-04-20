package ru.tcynik.meshtactics.data.user.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

class FakeAppUserRepository : AppUserRepository {

    private val _user = MutableStateFlow(AppUser(displayName = ""))

    override fun observeUser(): Flow<AppUser> = _user.asStateFlow()

    override suspend fun saveUser(user: AppUser) {
        _user.value = user
    }
}
