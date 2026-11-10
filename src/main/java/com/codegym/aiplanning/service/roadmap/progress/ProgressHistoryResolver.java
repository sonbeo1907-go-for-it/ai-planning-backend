package com.codegym.aiplanning.service.roadmap.progress;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Resolves append-only progress entries into their effective logical history. */
public final class ProgressHistoryResolver {

    private ProgressHistoryResolver() {}

    /** A correction supplies the result, while the original entry supplies the activity date. */
    public record EffectiveEvent(ProgressEntry entry, Instant activityRecordedAt) {}

    public static List<EffectiveEvent> effectiveEvents(List<ProgressEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProgressEntry> entriesById = new HashMap<>();
        for (ProgressEntry entry : entries) {
            if (entry.getId() != null) {
                entriesById.put(entry.getId(), entry);
            }
        }
        return effectiveEntries(entries).stream()
                .map(entry -> new EffectiveEvent(
                        entry, rootEntry(entry, entriesById).getRecordedAt()))
                .toList();
    }

    public static List<ProgressEntry> effectiveEntries(List<ProgressEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Map<UUID, ProgressEntry> entriesById = new HashMap<>();
        Set<UUID> supersededEntryIds = new HashSet<>();
        for (ProgressEntry entry : entries) {
            if (entry.getId() != null) {
                entriesById.put(entry.getId(), entry);
            }
            if (entry.getSupersedesEntryId() != null) {
                supersededEntryIds.add(entry.getSupersedesEntryId());
            }
        }

        List<ProgressEntry> effectiveEntries = new ArrayList<>();
        for (ProgressEntry entry : entries) {
            if (entry.getId() == null || !supersededEntryIds.contains(entry.getId())) {
                effectiveEntries.add(entry);
            }
        }

        effectiveEntries.sort(Comparator
                .comparing(
                        (ProgressEntry entry) -> logicalRecordedAt(entry, entriesById),
                        Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
                .thenComparing(
                        (ProgressEntry entry) -> logicalId(entry, entriesById),
                        Comparator.nullsFirst(Comparator.<UUID>naturalOrder())));
        return List.copyOf(effectiveEntries);
    }

    private static Instant logicalRecordedAt(
            ProgressEntry entry, Map<UUID, ProgressEntry> entriesById) {
        return rootEntry(entry, entriesById).getRecordedAt();
    }

    private static UUID logicalId(
            ProgressEntry entry, Map<UUID, ProgressEntry> entriesById) {
        return rootEntry(entry, entriesById).getId();
    }

    private static ProgressEntry rootEntry(
            ProgressEntry entry, Map<UUID, ProgressEntry> entriesById) {
        ProgressEntry current = entry;
        Set<UUID> visitedIds = new HashSet<>();
        while (current.getSupersedesEntryId() != null
                && visitedIds.add(current.getSupersedesEntryId())) {
            ProgressEntry parent = entriesById.get(current.getSupersedesEntryId());
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return current;
    }
}
