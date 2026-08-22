package ru.itmo.nemat.weezzy.lunch.request.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.LunchExtensionRequestedPayload;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LunchRequestLifecycleService {
	private final LunchRequestLifecycleRepository lifecycleRepository;
	private final OutboxEventService outboxEventService;
	private final LunchProperties properties;

	@Transactional
	public List<UUID> expireRequests(LocalDateTime now, int batchSize) {
		LocalTime latestTimeSlot = latestExtendableTimeSlot();
		List<LunchRequest> requests = lifecycleRepository.findExpirableForUpdate(
				now,
				minimumExtendableTimeSlot(now),
				latestTimeSlot,
				properties.maxExtensions(),
				batchSize
		);
		List<UUID> expiredRequestIds = new ArrayList<>(requests.size());
		for (LunchRequest request : requests) {
			if (expireRequest(request, now)) {
				expiredRequestIds.add(request.getId());
			}
		}
		return List.copyOf(expiredRequestIds);
	}

	@Transactional
	public List<UUID> offerExtensions(LocalDateTime now, int batchSize) {
		LocalTime latestTimeSlot = latestExtendableTimeSlot();
		List<LunchRequest> dueRequests = lifecycleRepository.findDueForUpdate(
				now,
				minimumExtendableTimeSlot(now),
				latestTimeSlot,
				properties.maxExtensions(),
				batchSize
		);
		List<UUID> offeredRequestIds = new ArrayList<>(dueRequests.size());
		for (LunchRequest request : dueRequests) {
			if (offerExtension(request, now)) {
				offeredRequestIds.add(request.getId());
			}
		}
		return List.copyOf(offeredRequestIds);
	}

	private boolean offerExtension(LunchRequest request, LocalDateTime now) {
		LocalDateTime targetTimeSlot = request.getTimeSlot().plus(
				properties.extensionDuration()
		);
		if (request.getStatus() != LunchRequestStatus.SEARCHING
				|| request.getExtensionCount() >= properties.maxExtensions()
				|| !targetTimeSlot.isAfter(now)
				|| !request.getTimeSlot().toLocalDate().equals(now.toLocalDate())
				|| !targetTimeSlot.toLocalDate().equals(
						request.getTimeSlot().toLocalDate()
				)
				|| targetTimeSlot.toLocalTime().isAfter(properties.windowEnd())) {
			return false;
		}

		UUID offerId = UUID.randomUUID();
		request.setStatus(LunchRequestStatus.EXTENSION_REQUESTED);
		request.setExtensionOfferId(offerId);
		request.setExtensionRequestedAt(now);
		LocalDateTime responseDeadline = now.plus(
				properties.extensionResponseTimeout()
		);
		request.setExtensionExpiresAt(
				responseDeadline.isBefore(targetTimeSlot)
						? responseDeadline
						: targetTimeSlot
		);
		request.setExtensionTargetTimeSlot(targetTimeSlot);
		outboxEventService.publish(new LunchExtensionRequestedPayload(
				request.getId(),
				offerId
		));
		return true;
	}

	private boolean expireRequest(LunchRequest request, LocalDateTime now) {
		boolean expiredOffer = request.getStatus()
				== LunchRequestStatus.EXTENSION_REQUESTED
				&& (request.getExtensionExpiresAt() == null
				|| request.getExtensionTargetTimeSlot() == null
				|| !request.getExtensionExpiresAt().isAfter(now)
				|| !request.getExtensionTargetTimeSlot().isAfter(now));
		boolean unextendableSearch = request.getStatus()
				== LunchRequestStatus.SEARCHING
				&& !request.getTimeSlot().isAfter(now)
				&& cannotExtend(request, now);
		if (!expiredOffer && !unextendableSearch) {
			return false;
		}
		request.setStatus(LunchRequestStatus.EXPIRED);
		return true;
	}

	private boolean cannotExtend(LunchRequest request, LocalDateTime now) {
		LocalDateTime targetTimeSlot = request.getTimeSlot().plus(
				properties.extensionDuration()
		);
		return request.getTimeSlot().toLocalDate().isBefore(now.toLocalDate())
				|| !targetTimeSlot.isAfter(now)
				|| request.getExtensionCount() >= properties.maxExtensions()
				|| !targetTimeSlot.toLocalDate().equals(
						request.getTimeSlot().toLocalDate()
				)
				|| targetTimeSlot.toLocalTime().isAfter(properties.windowEnd());
	}

	private LocalTime latestExtendableTimeSlot() {
		return properties.windowEnd().minus(properties.extensionDuration());
	}

	private LocalDateTime minimumExtendableTimeSlot(LocalDateTime now) {
		return now.minus(properties.extensionDuration());
	}
}
