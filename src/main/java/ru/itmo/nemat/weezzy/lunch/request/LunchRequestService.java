package ru.itmo.nemat.weezzy.lunch.request;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationService;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;
import ru.itmo.nemat.weezzy.lunch.request.dto.CreateLunchRequest;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LunchRequestService {
	private static final Set<LunchRequestStatus> ACTIVE_STATUSES = EnumSet.of(
			LunchRequestStatus.SEARCHING,
			LunchRequestStatus.EXTENSION_REQUESTED
	);

	private final LunchRequestRepository lunchRequestRepository;
	private final ProfileService profileService;
	private final LocationService locationService;
	private final LunchProperties properties;
	private final Clock clock;

	@Transactional
	public LunchRequest create(UUID userId, CreateLunchRequest request) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		ensureProfileActive(profile);
		if (lunchRequestRepository.existsByProfileIdAndStatusIn(
				profile.getId(),
				ACTIVE_STATUSES
		)) {
			throw new ActiveLunchRequestAlreadyExistsException(profile.getId());
		}

		LocalDateTime now = now();
		ensureNoMatchToday(profile.getId(), now.toLocalDate());
		ensureRequestWindowOpen(now.toLocalTime());
		LocalDateTime timeSlot = resolveTimeSlot(now, request.time());
		ensureTimeSlotWithinWindow(timeSlot, now.toLocalDate());
		Location location = locationService.findById(request.locationId());

		LunchRequest lunchRequest = new LunchRequest();
		lunchRequest.setProfile(profile);
		lunchRequest.setLocation(location);
		lunchRequest.setTopic(request.topic());
		lunchRequest.setComment(normalizeComment(request.comment()));
		lunchRequest.setTimeSlot(timeSlot);
		return lunchRequestRepository.save(lunchRequest);
	}

	@Transactional(readOnly = true)
	public LunchRequest findActiveForUser(UUID userId) {
		Profile profile = profileService.findByUserId(userId);
		return lunchRequestRepository
				.findFirstByProfileIdAndStatusInOrderByCreatedAtDesc(
						profile.getId(),
						ACTIVE_STATUSES
				)
				.orElseThrow(() -> new LunchRequestNotFoundException(profile.getId()));
	}

	@Transactional
	public LunchRequest cancelCurrent(UUID userId) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		LunchRequest request = lunchRequestRepository
				.findFirstByProfileIdOrderByCreatedAtDesc(profile.getId())
				.orElseThrow(() -> new LunchRequestNotFoundException(profile.getId()));

		if (request.getStatus() == LunchRequestStatus.CANCELLED) {
			return request;
		}
		if (!ACTIVE_STATUSES.contains(request.getStatus())) {
			throw new InvalidLunchRequestStateException(
					request.getId(),
					request.getStatus(),
					"cancel"
			);
		}

		request.setStatus(LunchRequestStatus.CANCELLED);
		request.setCancelledAt(now());
		return request;
	}

	@Transactional
	public LunchRequest extendCurrent(UUID userId, UUID offerId) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		LunchRequest request = lunchRequestRepository
				.findActiveForUpdate(profile.getId(), ACTIVE_STATUSES)
				.orElseThrow(() -> new LunchRequestNotFoundException(profile.getId()));
		LocalDateTime now = now();

		if (!Objects.equals(request.getExtensionOfferId(), offerId)) {
			throw new LunchExtensionOfferMismatchException(request.getId());
		}
		if (request.getStatus() == LunchRequestStatus.SEARCHING) {
			return request;
		}
		if (request.getStatus() != LunchRequestStatus.EXTENSION_REQUESTED) {
			throw new InvalidLunchRequestStateException(
					request.getId(),
					request.getStatus(),
					"extend"
			);
		}
		if (request.getExtensionCount() >= properties.maxExtensions()) {
			throw new LunchExtensionLimitReachedException(
					request.getId(),
					properties.maxExtensions()
			);
		}
		ensureExtensionOfferActive(request, now);

		LocalDateTime extendedTimeSlot = request.getExtensionTargetTimeSlot();
		ensureTimeSlotWithinWindow(extendedTimeSlot, request.getTimeSlot().toLocalDate());
		request.setTimeSlot(extendedTimeSlot);
		request.setExtensionCount(request.getExtensionCount() + 1);
		request.setStatus(LunchRequestStatus.SEARCHING);
		return request;
	}

	private void ensureProfileActive(Profile profile) {
		if (profile.getStatus() != ProfileStatus.ACTIVE) {
			throw new LunchProfileNotActiveException(profile.getId());
		}
	}

	private void ensureNoMatchToday(UUID profileId, LocalDate currentDate) {
		LocalDateTime dayStart = currentDate.atStartOfDay();
		if (lunchRequestRepository
				.existsByProfileIdAndStatusAndTimeSlotGreaterThanEqualAndTimeSlotLessThan(
						profileId,
						LunchRequestStatus.MATCHED,
						dayStart,
						dayStart.plusDays(1)
				)) {
			throw new LunchAlreadyMatchedTodayException(profileId, currentDate);
		}
	}

	private void ensureRequestWindowOpen(LocalTime currentTime) {
		if (currentTime.isBefore(properties.windowStart())
				|| currentTime.isAfter(properties.windowEnd())) {
			throw new LunchRequestWindowClosedException(
					properties.windowStart(),
					properties.windowEnd(),
					properties.zoneId()
			);
		}
	}

	private LocalDateTime resolveTimeSlot(
			LocalDateTime now,
			LunchTimeOption timeOption
	) {
		LocalDateTime candidate = now.plus(timeOption.offset());
		LocalDateTime dayStart = candidate.toLocalDate().atStartOfDay();
		long elapsedMinutes = ChronoUnit.MINUTES.between(dayStart, candidate);
		if (candidate.getSecond() > 0 || candidate.getNano() > 0) {
			elapsedMinutes++;
		}
		long intervalMinutes = properties.slotInterval().toMinutes();
		long roundedMinutes = Math.ceilDiv(elapsedMinutes, intervalMinutes)
				* intervalMinutes;
		return dayStart.plusMinutes(roundedMinutes);
	}

	private void ensureTimeSlotWithinWindow(
			LocalDateTime timeSlot,
			LocalDate expectedDate
	) {
		if (!timeSlot.toLocalDate().equals(expectedDate)
				|| timeSlot.toLocalTime().isBefore(properties.windowStart())
				|| timeSlot.toLocalTime().isAfter(properties.windowEnd())) {
			throw new LunchTimeSlotOutsideWindowException(timeSlot);
		}
	}

	private void ensureExtensionOfferActive(
			LunchRequest request,
			LocalDateTime now
	) {
		if (request.getExtensionRequestedAt() == null
				|| request.getExtensionExpiresAt() == null
				|| request.getExtensionTargetTimeSlot() == null) {
			throw new InvalidLunchRequestStateException(
					request.getId(),
					request.getStatus(),
					"extend"
			);
		}
		if (!now.isBefore(request.getExtensionExpiresAt())) {
			throw new LunchExtensionOfferExpiredException(request.getId());
		}
	}

	private String normalizeComment(String comment) {
		if (comment == null) {
			return null;
		}
		String normalized = comment.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
