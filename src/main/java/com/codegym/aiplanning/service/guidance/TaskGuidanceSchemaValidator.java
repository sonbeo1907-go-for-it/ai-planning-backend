package com.codegym.aiplanning.service.guidance;

import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance.GeneratedReference;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance.GeneratedStepGuidance;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TaskGuidanceSchemaValidator {

    private static final int MAX_REFERENCES_PER_SCOPE = 8;
    private static final int MAX_TOTAL_REFERENCES = 20;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "dailyPlanVersionId",
            "dailyPlanItemId",
            "objective",
            "taskSummary",
            "stepGuidances",
            "references");
    private static final Set<String> STEP_FIELDS = Set.of(
            "taskStepId",
            "instructions",
            "expectedResult",
            "tips",
            "cautions",
            "prerequisites",
            "references");
    private static final Set<String> SOURCE_REFERENCE_FIELDS = Set.of(
            "provenance",
            "displayLabel",
            "locator",
            "targetId");
    private static final Set<String> EXTERNAL_REFERENCE_FIELDS = Set.of(
            "provenance",
            "displayLabel",
            "url");
    private static final List<String> MUTATION_PHRASES = List.of(
            "mark this task as completed",
            "set task status",
            "update progress",
            "delete the task",
            "reorder the task",
            "create a weak topic",
            "mark weak topic",
            "submit the quiz",
            "danh dau task hoan thanh",
            "cap nhat tien do",
            "xoa nhiem vu");

    private final ObjectMapper objectMapper;

    public TaskGuidanceSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedTaskGuidance validate(
            String rawResponse,
            TaskGuidanceContext context) {
        JsonNode root = parseObject(rawResponse);
        requireExactFields(root, ROOT_FIELDS, "root");

        UUID versionId = requireUuid(root, "dailyPlanVersionId", "root");
        UUID itemId = requireUuid(root, "dailyPlanItemId", "root");
        if (!context.dailyPlanVersionId().equals(versionId)
                || !context.dailyPlanItemId().equals(itemId)) {
            throw invalid("The response does not match the requested Daily Plan item.");
        }

        String objective = requireText(root, "objective", 500, "root");
        String taskSummary = requireText(root, "taskSummary", 4_000, "root");
        requireAdvisoryContent(objective);
        requireAdvisoryContent(taskSummary);
        if (sameNormalizedText(objective, context.title())
                || sameNormalizedText(taskSummary, context.title())) {
            throw invalid("Task-level guidance must add useful execution detail.");
        }

        Set<String> referenceKeys = new HashSet<>();
        List<GeneratedReference> taskReferences = parseReferences(
                requireArray(root, "references", "root"),
                context,
                referenceKeys,
                "root.references");
        Map<UUID, GeneratedStepGuidance> guidanceByStep = parseStepGuidances(
                requireArray(root, "stepGuidances", "root"),
                context,
                referenceKeys);

        if (referenceKeys.size() > MAX_TOTAL_REFERENCES) {
            throw invalid("The response contains too many references.");
        }

        List<GeneratedStepGuidance> orderedGuidance = context.taskSteps().stream()
                .map(step -> guidanceByStep.get(step.id()))
                .toList();
        return new GeneratedTaskGuidance(
                versionId,
                itemId,
                objective,
                taskSummary,
                orderedGuidance,
                taskReferences);
    }

    private Map<UUID, GeneratedStepGuidance> parseStepGuidances(
            JsonNode stepsNode,
            TaskGuidanceContext context,
            Set<String> referenceKeys) {
        if (stepsNode.size() != context.taskSteps().size()) {
            throw invalid("The response must contain exactly one entry per Task Step.");
        }

        Map<UUID, TaskGuidanceContext.TaskStepSnapshot> allowedSteps =
                context.taskSteps().stream().collect(java.util.stream.Collectors.toMap(
                        TaskGuidanceContext.TaskStepSnapshot::id,
                        step -> step));
        Map<UUID, GeneratedStepGuidance> result = new HashMap<>();
        for (int index = 0; index < stepsNode.size(); index++) {
            JsonNode node = stepsNode.get(index);
            requireObject(node, "stepGuidances[" + index + "]");
            requireExactFields(node, STEP_FIELDS, "stepGuidances[" + index + "]");
            UUID stepId = requireUuid(node, "taskStepId", "stepGuidances[" + index + "]");
            if (!allowedSteps.containsKey(stepId) || result.containsKey(stepId)) {
                throw invalid("The response contains a missing, duplicate, or foreign Task Step ID.");
            }

            String instructions = requireText(
                    node,
                    "instructions",
                    2_000,
                    "stepGuidances[" + index + "]");
            String expectedResult = requireText(
                    node,
                    "expectedResult",
                    1_000,
                    "stepGuidances[" + index + "]");
            String tips = nullableText(node, "tips", 1_000, "stepGuidances[" + index + "]");
            String cautions = nullableText(
                    node,
                    "cautions",
                    1_000,
                    "stepGuidances[" + index + "]");
            String prerequisites = nullableText(
                    node,
                    "prerequisites",
                    1_000,
                    "stepGuidances[" + index + "]");
            requireAdvisoryContent(instructions);
            requireAdvisoryContent(expectedResult);
            requireAdvisoryContent(tips);
            requireAdvisoryContent(cautions);
            requireAdvisoryContent(prerequisites);
            TaskGuidanceContext.TaskStepSnapshot sourceStep = allowedSteps.get(stepId);
            if (sameNormalizedText(instructions, sourceStep.title())
                    || sameNormalizedText(instructions, sourceStep.plannedGuidance())) {
                throw invalid("Task Step guidance must explain rather than repeat the step.");
            }

            List<GeneratedReference> references = parseReferences(
                    requireArray(node, "references", "stepGuidances[" + index + "]"),
                    context,
                    referenceKeys,
                    "stepGuidances[" + index + "].references");
            result.put(stepId, new GeneratedStepGuidance(
                    stepId,
                    instructions,
                    expectedResult,
                    tips,
                    cautions,
                    prerequisites,
                    references));
        }
        return result;
    }

    private List<GeneratedReference> parseReferences(
            JsonNode referencesNode,
            TaskGuidanceContext context,
            Set<String> referenceKeys,
            String fieldPath) {
        if (referencesNode.size() > MAX_REFERENCES_PER_SCOPE) {
            throw invalid(fieldPath + " contains too many references.");
        }

        List<GeneratedReference> references = new ArrayList<>();
        for (int index = 0; index < referencesNode.size(); index++) {
            JsonNode node = referencesNode.get(index);
            String path = fieldPath + "[" + index + "]";
            requireObject(node, path);
            GuidanceReferenceProvenance provenance = requireProvenance(node, path);
            GeneratedReference reference = provenance
                    == GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL
                    ? parseExternalReference(node, path)
                    : parseSourceReference(node, context, provenance, path);
            String key = canonicalReferenceKey(reference);
            if (!referenceKeys.add(key)) {
                throw invalid("The response contains a duplicate reference.");
            }
            references.add(reference);
        }
        return List.copyOf(references);
    }

    private GeneratedReference parseSourceReference(
            JsonNode node,
            TaskGuidanceContext context,
            GuidanceReferenceProvenance provenance,
            String path) {
        requireExactFields(node, SOURCE_REFERENCE_FIELDS, path);
        UUID targetId = requireUuid(node, "targetId", path);
        boolean allowed = switch (provenance) {
            case MATERIAL -> context.allowedMaterialIds().contains(targetId);
            case LEARNING_SOURCE -> context.allowedLearningSourceIds().contains(targetId);
            case ROADMAP_CONTEXT -> context.allowedRoadmapItemIds().contains(targetId);
            case UNVERIFIED_EXTERNAL -> false;
        };
        if (!allowed) {
            throw invalid("A reference target was not supplied in the generation context.");
        }
        return new GeneratedReference(
                provenance,
                requireText(node, "displayLabel", 500, path),
                nullableText(node, "locator", 1_000, path),
                targetId,
                null);
    }

    private GeneratedReference parseExternalReference(JsonNode node, String path) {
        requireExactFields(node, EXTERNAL_REFERENCE_FIELDS, path);
        String url = requireText(node, "url", 2_048, path);
        requireSafeExternalUrl(url);
        return new GeneratedReference(
                GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL,
                requireText(node, "displayLabel", 500, path),
                null,
                null,
                url);
    }

    private void requireSafeExternalUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw invalid("An external reference URL is unsafe.");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost")
                    || host.endsWith(".localhost")
                    || host.endsWith(".local")
                    || isPrivateIpv4(host)
                    || isPrivateIpv6(host)) {
                throw invalid("An external reference URL targets a local or private address.");
            }
        } catch (URISyntaxException exception) {
            throw invalid("An external reference URL is invalid.");
        }
    }

    private boolean isPrivateIpv4(String host) {
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] octets = host.split("\\.");
        int first = Integer.parseInt(octets[0]);
        int second = Integer.parseInt(octets[1]);
        if (java.util.Arrays.stream(octets)
                .mapToInt(Integer::parseInt)
                .anyMatch(value -> value > 255)) {
            return true;
        }
        return first == 0
                || first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || first >= 224;
    }

    private boolean isPrivateIpv6(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("::1")
                || normalized.equals("::")
                || normalized.startsWith("fc")
                || normalized.startsWith("fd")
                || normalized.startsWith("fe8")
                || normalized.startsWith("fe9")
                || normalized.startsWith("fea")
                || normalized.startsWith("feb");
    }

    private String canonicalReferenceKey(GeneratedReference reference) {
        String target = reference.targetId() != null
                ? reference.targetId().toString()
                : reference.externalUrl().strip().toLowerCase(Locale.ROOT);
        return reference.provenance().name() + ':' + target;
    }

    private GuidanceReferenceProvenance requireProvenance(
            JsonNode node,
            String path) {
        String value = requireText(node, "provenance", 30, path);
        try {
            return GuidanceReferenceProvenance.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("A reference contains an unsupported provenance type.");
        }
    }

    private JsonNode parseObject(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw invalid("The AI response is empty.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            requireObject(root, "root");
            return root;
        } catch (JsonProcessingException exception) {
            throw invalid("The AI response is not valid JSON.");
        }
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " must be a JSON object.");
        }
    }

    private void requireExactFields(
            JsonNode node,
            Set<String> expected,
            String path) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid(path + " contains missing or unknown fields.");
        }
    }

    private JsonNode requireArray(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw invalid(path + '.' + field + " must be an array.");
        }
        return value;
    }

    private UUID requireUuid(JsonNode node, String field, String path) {
        String value = requireText(node, field, 36, path);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + '.' + field + " must be a UUID.");
        }
    }

    private String requireText(
            JsonNode node,
            String field,
            int maximumLength,
            String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(path + '.' + field + " must be a non-blank string.");
        }
        String normalized = value.textValue().strip();
        if (normalized.length() > maximumLength) {
            throw invalid(path + '.' + field + " exceeds its length limit.");
        }
        return normalized;
    }

    private String nullableText(
            JsonNode node,
            String field,
            int maximumLength,
            String path) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(path + '.' + field + " must be a string or null.");
        }
        String normalized = value.textValue().strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maximumLength) {
            throw invalid(path + '.' + field + " exceeds its length limit.");
        }
        return normalized;
    }

    private void requireAdvisoryContent(String value) {
        if (value == null) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (MUTATION_PHRASES.stream().anyMatch(normalized::contains)) {
            throw invalid("Guidance must not attempt to mutate application state.");
        }
    }

    private boolean sameNormalizedText(String first, String second) {
        return first != null
                && second != null
                && first.strip().equalsIgnoreCase(second.strip());
    }

    private InvalidAiTaskGuidanceResponseException invalid(String message) {
        return new InvalidAiTaskGuidanceResponseException(message);
    }
}
