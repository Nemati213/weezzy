package ru.itmo.nemat.weezzy.recommendation;

import java.util.UUID;

public record RecommendationCursor(int score, UUID profileId) {
}
