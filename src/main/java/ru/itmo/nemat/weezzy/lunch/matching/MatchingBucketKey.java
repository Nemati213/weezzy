package ru.itmo.nemat.weezzy.lunch.matching;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record MatchingBucketKey(UUID locationId, LocalDateTime timeSlot) {
	private static final long LOCK_NAMESPACE = 0x4C554E43484D4154L;

	public MatchingBucketKey {
		Objects.requireNonNull(locationId, "locationId must not be null");
		Objects.requireNonNull(timeSlot, "timeSlot must not be null");
	}

	public long advisoryLockKey() {
		long value = LOCK_NAMESPACE
				^ locationId.getMostSignificantBits()
				^ Long.rotateLeft(locationId.getLeastSignificantBits(), 19)
				^ Long.rotateLeft(timeSlot.toLocalDate().toEpochDay(), 37)
				^ timeSlot.toLocalTime().toNanoOfDay();
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		return value ^ value >>> 33;
	}
}
