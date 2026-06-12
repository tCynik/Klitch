package ru.tcynik.klitch.data.local.group

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.group.model.GroupModel
import ru.tcynik.klitch.domain.group.model.GroupRole
import ru.tcynik.klitch.domain.group.repository.GroupRepository

class LocalGroupRepositoryImpl : GroupRepository {
    override fun observeGroups(): Flow<List<GroupModel>> = TODO("SQLDelight implementation pending")
    override suspend fun createGroup(name: String, channelId: String): GroupModel = TODO("SQLDelight implementation pending")
    override suspend fun deleteGroup(groupId: String) = TODO("SQLDelight implementation pending")
    override suspend fun addMember(groupId: String, nodeId: String) = TODO("SQLDelight implementation pending")
    override suspend fun removeMember(groupId: String, nodeId: String) = TODO("SQLDelight implementation pending")
    override suspend fun setMemberRole(groupId: String, nodeId: String, role: GroupRole) = TODO("SQLDelight implementation pending")
}
