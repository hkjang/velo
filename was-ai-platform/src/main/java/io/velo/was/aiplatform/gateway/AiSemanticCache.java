package io.velo.was.aiplatform.gateway;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class AiSemanticCache {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    LookupResult lookup(String modelName, String prompt, double threshold) {
        Set<String> queryTokens = tokens(prompt);
        if (queryTokens.isEmpty()) {
            return null;
        }
        Entry best = null;
        double bestScore = 0.0d;
        for (Entry entry : entries.values()) {
            if (!entry.modelName().equalsIgnoreCase(modelName)) {
                continue;
            }
            double score = jaccard(queryTokens, entry.tokens());
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return best != null && bestScore >= threshold ? new LookupResult(best, bestScore) : null;
    }

    void put(String modelName, String prompt, AiGatewayInferenceResult result, int maxEntries) {
        if (entries.size() >= maxEntries) {
            String oldestKey = entries.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().createdAtEpochMillis()))
                    .map(java.util.Map.Entry::getKey)
                    .orElse(null);
            if (oldestKey != null) {
                entries.remove(oldestKey);
            }
        }
        entries.put(modelName + ':' + Integer.toHexString(prompt.hashCode()),
                new Entry(modelName, tokens(prompt), result.outputText(), result.estimatedTokens(),
                        result.confidence(), System.currentTimeMillis()));
    }

    int size() {
        return entries.size();
    }

    private static Set<String> tokens(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ");
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .forEach(result::add);
        return result;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0.0d : (double) intersection / union;
    }

    record Entry(String modelName, Set<String> tokens, String outputText,
                 int estimatedTokens, double confidence, long createdAtEpochMillis) {
    }

    record LookupResult(Entry entry, double score) {
    }
}
