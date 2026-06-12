package ru.tcynik.klitch.domain.group.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.group.model.GroupModel
import ru.tcynik.klitch.domain.group.model.GroupRole

interface GroupRepository {
    fun observeGroups(): Flow<List<GroupModel>>
    suspend fun createGroup(name: String, channelId: String): GroupModel
    suspend fun deleteGroup(groupId: String)
    suspend fun addMember(groupId: String, nodeId: String)
    suspend fun removeMember(groupId: String, nodeId: String)
    suspend fun setMemberRole(groupId: String, nodeId: String, role: GroupRole)
}
