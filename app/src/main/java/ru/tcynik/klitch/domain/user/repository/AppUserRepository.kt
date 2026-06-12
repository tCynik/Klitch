package ru.tcynik.klitch.domain.user.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.user.model.AppUser

interface AppUserRepository {
    fun observeUser(): Flow<AppUser>
    suspend fun saveUser(user: AppUser)
}
