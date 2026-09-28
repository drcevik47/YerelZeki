package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "agent_sessions")
data class AgentSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelName: String = "Gemma 3n",
    val projectFolder: String = "default"
)

@Entity(
    tableName = "agent_messages",
    indices = [Index("sessionId")]
)
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sender: String, // "user", "agent", "system", "tool"
    val content: String,
    val thought: String? = null,
    val toolCallName: String? = null,
    val toolCallArgs: String? = null,
    val toolResult: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS" // "RUNNING", "SUCCESS", "ERROR"
)

@Entity(
    tableName = "tool_executions",
    indices = [Index("sessionId")]
)
data class ToolExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val toolName: String,
    val inputArgs: String,
    val outputResult: String,
    val executionTimeMs: Long,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspace_projects")
data class WorkspaceProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val directoryPath: String,
    val description: String = "",
    val fileCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
