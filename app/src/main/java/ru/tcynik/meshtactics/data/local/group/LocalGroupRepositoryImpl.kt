package ru.tcynik.meshtactics.data.local.group

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.group.model.GroupModel
import ru.tcynik.meshtactics.domain.group.model.GroupRole
import ru.tcynik.meshtactics.domain.group.repository.GroupRepository

class LocalGroupRepositoryImpl : GroupRepository {
    override fun observeGroups(): Flow<List<GroupModel>> = TODO("SQLDelight implementation pending")
    override suspend fun createGroup(name: String, channelId: String): GroupModel = TODO("SQLDelight implementation pending")
    override suspend fun deleteGroup(groupId: String) = TODO("SQLDelight implementation pending")
    override suspend fun addMember(groupId: String, nodeId: String) = TODO("SQLDelight implementation pending")
    override suspend fun removeMember(groupId: String, nodeId: String) = TODO("SQLDelight implementation pending")
    override suspend fun setMemberRole(groupId: String, nodeId: String, role: GroupRole) = TODO("SQLDelight implementation pending")
}
