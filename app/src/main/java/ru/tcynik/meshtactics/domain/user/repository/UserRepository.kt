package ru.tcynik.meshtactics.domain.user.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.user.model.UserProfileModel

interface UserRepository {
    fun observeProfile(): Flow<UserProfileModel?>
    suspend fun saveProfile(profile: UserProfileModel)
}
