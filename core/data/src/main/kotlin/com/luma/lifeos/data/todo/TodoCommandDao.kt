package com.luma.lifeos.data.todo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TodoCommandDao {
    @Query("SELECT * FROM todo_family_command WHERE id = :id LIMIT 1")
    suspend fun find(id: String): TodoCommandEntity?

    @Query("SELECT * FROM todo_family_command WHERE command_id = :commandId LIMIT 1")
    suspend fun findByCommandId(commandId: String): TodoCommandEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: TodoCommandEntity)

    @Update
    suspend fun update(command: TodoCommandEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<TodoCommandMemberEntity>)

    @Query("SELECT * FROM todo_family_command_member WHERE command_id = :commandId ORDER BY task_id")
    suspend fun members(commandId: String): List<TodoCommandMemberEntity>

    @Update
    suspend fun updateMembers(members: List<TodoCommandMemberEntity>): Int

    @Query("DELETE FROM todo_family_command WHERE pinned = 0 AND terminal_at IS NOT NULL AND terminal_at <= :cutoff")
    suspend fun pruneTerminal(cutoff: Long): Int

    @Query("""
        UPDATE todo_family_command
        SET state = CASE WHEN state = 'PREPARED' THEN 'STALE' ELSE state END,
            pinned = 0,
            terminal_at = CASE WHEN state = 'PREPARED' THEN :now ELSE terminal_at END,
            undo_available = CASE WHEN state = 'PREPARED' THEN 0 ELSE undo_available END
        WHERE process_token IS NOT NULL AND process_token != :activeToken
    """)
    suspend fun reconcileForeignHandles(activeToken: String, now: Long): Int

    @Query("""
        UPDATE todo_family_command SET state = 'STALE', pinned = 0, terminal_at = :now,
            undo_available = 0
        WHERE id = :id AND state = 'PREPARED'
    """)
    suspend fun markStale(id: String, now: Long): Int

    @Query("""
        UPDATE todo_family_command
        SET pinned = 0,
            state = CASE WHEN state = 'PREPARED' THEN 'STALE' ELSE state END,
            terminal_at = CASE WHEN state = 'PREPARED' THEN :now ELSE terminal_at END
        WHERE id = :id OR command_id = :id
    """)
    suspend fun release(id: String, now: Long): Int

    @Query("DELETE FROM todo_family_command")
    suspend fun clearAll(): Int
}
