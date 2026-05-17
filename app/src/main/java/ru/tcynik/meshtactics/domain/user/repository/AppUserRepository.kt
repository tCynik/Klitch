package ru.tcynik.meshtactics.domain.user.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.user.model.AppUser

interface AppUserRepository {
    fun observeUser(): Flow<AppUser>
    suspend fun saveUser(user: AppUser)
}
