package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {

    // --- Sessions ---
    @Query("SELECT * FROM agent_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<AgentSessionEntity>>

    @Query("SELECT * FROM agent_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): AgentSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AgentSessionEntity)

    @Update
    suspend fun updateSession(session: AgentSessionEntity)

    @Query("DELETE FROM agent_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    // --- Messages ---
    @Query("SELECT * FROM agent_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<AgentMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AgentMessageEntity): Long

    @Update
    suspend fun updateMessage(message: AgentMessageEntity)

    @Query("DELETE FROM agent_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesForSession(sessionId: String)

    // --- Tool Executions ---
    @Query("SELECT * FROM tool_executions WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getToolExecutionsForSession(sessionId: String): Flow<List<ToolExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolExecution(execution: ToolExecutionEntity): Long

    // --- Projects ---
    @Query("SELECT * FROM workspace_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<WorkspaceProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: WorkspaceProjectEntity)

    @Query("DELETE FROM workspace_projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)
}
