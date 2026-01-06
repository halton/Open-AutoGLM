package com.openautoglm.agent.agent

import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.InferenceMode
import java.util.UUID

/**
 * Handler for re-executing tasks from history.
 *
 * Allows users to redo a previously executed task with the same parameters.
 * Creates a new task instance based on an existing task's configuration.
 */
object TaskRedoHandler {

    /**
     * Result of preparing a task for redo.
     */
    data class RedoTaskInfo(
        val description: String,
        val inferenceMode: InferenceMode,
        val maxSteps: Int
    )

    /**
     * Extracts redo information from a completed/failed task.
     *
     * Creates a new task info object that can be used to start a fresh task
     * with the same parameters as the original.
     *
     * @param task The original task to redo
     * @return RedoTaskInfo containing the task parameters for re-execution
     */
    fun prepareForRedo(task: Task): RedoTaskInfo {
        return RedoTaskInfo(
            description = task.description,
            inferenceMode = task.inferenceMode,
            maxSteps = task.maxSteps
        )
    }

    /**
     * Creates a new task instance for re-execution.
     *
     * The new task will have:
     * - A new unique ID
     * - The same description as the original
     * - The same inference mode
     * - The same max steps configuration
     * - Fresh timestamps and counters
     *
     * @param originalTask The task to create a copy from
     * @return A new Task instance ready for execution
     */
    fun createRedoTask(originalTask: Task): Task {
        return Task(
            id = UUID.randomUUID(),
            description = originalTask.description,
            inferenceMode = originalTask.inferenceMode,
            maxSteps = originalTask.maxSteps
            // All other fields use defaults: PENDING status, current timestamp, etc.
        )
    }

    /**
     * Validates if a task can be redone.
     *
     * A task can be redone if it has a non-empty description.
     * Active tasks (PENDING, RUNNING, PAUSED) should not be redone
     * as they are still in progress.
     *
     * @param task The task to validate
     * @return true if the task can be redone, false otherwise
     */
    fun canRedo(task: Task): Boolean {
        return task.description.isNotBlank()
    }
}
