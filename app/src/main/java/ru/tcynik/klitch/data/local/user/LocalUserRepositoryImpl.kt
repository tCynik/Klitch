package ru.tcynik.klitch.data.local.user

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.user.model.UserProfileModel
import ru.tcynik.klitch.domain.user.repository.UserRepository

class LocalUserRepositoryImpl : UserRepository {
    override fun observeProfile(): Flow<UserProfileModel?> = TODO("Multiplatform Settings implementation pending")
    override suspend fun saveProfile(profile: UserProfileModel) = TODO("Multiplatform Settings implementation pending")
}
