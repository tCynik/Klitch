package ru.tcynik.meshtactics.data.local.user

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.user.model.UserProfileModel
import ru.tcynik.meshtactics.domain.user.repository.UserRepository

class LocalUserRepositoryImpl : UserRepository {
    override fun observeProfile(): Flow<UserProfileModel?> = TODO("Multiplatform Settings implementation pending")
    override suspend fun saveProfile(profile: UserProfileModel) = TODO("Multiplatform Settings implementation pending")
}
