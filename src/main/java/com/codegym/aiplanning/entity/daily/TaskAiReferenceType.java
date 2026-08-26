package com.codegym.aiplanning.entity.daily;

/**
 * Kind of reference attached to an AI-generated task suggestion (US-TSK-AI).
 *
 * <p>{@code DOCUMENT} references one original learning document owned by the
 * user (a Material or a LearningSource linked to the user's Roadmap). Such
 * references are always {@code verified}. {@code LINK} references an external
 * path proposed by the AI that is not part of the user's original documents;
 * those links are always unverified and must be displayed with the
 * "Gợi ý chưa xác minh" badge.
 */
public enum TaskAiReferenceType {
    DOCUMENT,
    LINK
}
