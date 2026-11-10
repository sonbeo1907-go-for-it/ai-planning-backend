package com.codegym.aiplanning.service.daily.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Matches source phrases as whole Unicode-normalized tokens, never raw substrings. */
final class LearningUnitAnchorMatcher {

    private static final int MAX_ANCHOR_LENGTH = 160;
    private static final int MAX_PROMPT_CANDIDATES = 8;
    private static final int MAX_REPAIR_CANDIDATES = 40;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "build", "compare", "create", "explain", "for", "from",
            "implement", "in", "learn", "of", "on", "practice", "read", "run", "study",
            "the", "to", "use", "with", "write",
            "bai", "ban", "bang", "cach", "cho", "co", "cua", "cac", "de", "dung",
            "hanh", "hieu", "hoc", "kiem", "la", "lam", "lap", "luyen", "mot", "su",
            "tap", "thuc", "tim", "tren", "trinh", "trong", "va", "voi");

    List<String> promptCandidates(String title, String description) {
        return candidates(title, description, MAX_PROMPT_CANDIDATES);
    }

    String repairAnchor(
            String currentAnchor,
            String stepTitle,
            String stepGuidance,
            String learningUnitTitle,
            String learningUnitDescription) {
        if (validAnchor(currentAnchor, stepTitle, stepGuidance,
                learningUnitTitle, learningUnitDescription)) {
            return currentAnchor;
        }
        for (String candidate : candidates(
                learningUnitTitle, learningUnitDescription, MAX_REPAIR_CANDIDATES)) {
            if (usedInStep(candidate, stepTitle, stepGuidance)) {
                return candidate;
            }
        }
        return currentAnchor;
    }

    boolean validLength(String anchor) {
        return anchor != null
                && !anchor.isBlank()
                && anchor.trim().length() <= MAX_ANCHOR_LENGTH
                && meaningful(anchor);
    }

    boolean belongsToLearningUnit(String anchor, String title, String description) {
        return containsPhrase(title, anchor) || containsPhrase(description, anchor);
    }

    boolean usedInStep(String anchor, String title, String guidance) {
        return containsPhrase(title, anchor) || containsPhrase(guidance, anchor);
    }

    private boolean validAnchor(
            String anchor,
            String stepTitle,
            String stepGuidance,
            String learningUnitTitle,
            String learningUnitDescription) {
        return validLength(anchor)
                && belongsToLearningUnit(anchor, learningUnitTitle, learningUnitDescription)
                && usedInStep(anchor, stepTitle, stepGuidance);
    }

    private List<String> candidates(String title, String description, int maxCandidates) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        addCandidates(found, title, maxCandidates);
        addCandidates(found, description, maxCandidates);
        return List.copyOf(found);
    }

    private void addCandidates(Set<String> found, String source, int maxCandidates) {
        if (source == null || found.size() >= maxCandidates) {
            return;
        }
        Matcher matcher = WORD.matcher(source);
        while (matcher.find() && found.size() < maxCandidates) {
            String word = matcher.group();
            if (meaningful(word)) {
                found.add(word);
            }
        }
    }

    private boolean meaningful(String phrase) {
        for (String token : tokens(phrase)) {
            if (!STOP_WORDS.contains(token)
                    && (token.length() >= 3
                            || (token.length() == 2 && phrase.trim().matches("[A-Z]{2}")))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPhrase(String text, String phrase) {
        List<String> textTokens = tokens(text);
        List<String> phraseTokens = tokens(phrase);
        if (phraseTokens.isEmpty() || phraseTokens.size() > textTokens.size()) {
            return false;
        }
        for (int start = 0; start <= textTokens.size() - phraseTokens.size(); start++) {
            if (textTokens.subList(start, start + phraseTokens.size()).equals(phraseTokens)) {
                return true;
            }
        }
        return false;
    }

    private List<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        Matcher matcher = WORD.matcher(normalized);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
