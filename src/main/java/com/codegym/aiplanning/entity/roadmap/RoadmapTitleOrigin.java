package com.codegym.aiplanning.entity.roadmap;

/** Identifies who currently has authority over a Roadmap title. */
public enum RoadmapTitleOrigin {
    USER,
    GOAL_DERIVED,
    AI_SUGGESTED,
    FALLBACK
}
