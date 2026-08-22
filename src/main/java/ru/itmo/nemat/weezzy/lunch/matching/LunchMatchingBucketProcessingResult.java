package ru.itmo.nemat.weezzy.lunch.matching;

public record LunchMatchingBucketProcessingResult(
		boolean claimed,
		int formedGroupCount,
		int matchedCandidateCount,
		int remainingRequestCount
) {
	public LunchMatchingBucketProcessingResult {
		if (formedGroupCount < 0
				|| matchedCandidateCount < 0
				|| remainingRequestCount < 0) {
			throw new IllegalArgumentException("processing counts must not be negative");
		}
	}

	public static LunchMatchingBucketProcessingResult notClaimed() {
		return new LunchMatchingBucketProcessingResult(false, 0, 0, 0);
	}
}
