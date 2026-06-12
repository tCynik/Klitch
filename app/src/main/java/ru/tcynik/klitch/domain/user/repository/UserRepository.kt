package ru.tcynik.klitch.domain.user.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.user.model.UserProfileModel

interface UserRepository {
    fun observeProfile(): Flow<UserProfileModel?>
    suspend fun saveProfile(profile: UserProfileModel)
}
