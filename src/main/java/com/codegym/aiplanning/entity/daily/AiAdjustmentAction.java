package com.codegym.aiplanning.entity.daily;

/**
 * Represents an adjustment action proposed by the AI for a daily plan task.
 * Note: These are proposals/metadata only. For example, DROP only removes the task 
 * from today's plan, it does NOT delete it from the original Roadmap.
 */
public enum AiAdjustmentAction {
    SPLIT,
    RESCHEDULE,
    CARRY_OVER,
    DROP
}
