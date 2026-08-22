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
	public List<UUID> offerExtensions(LocalDateTime now, int batchSize) {
		LocalTime latestTimeSlot = properties.windowEnd().minus(
				properties.extensionDuration()
		);
		List<LunchRequest> dueRequests = lifecycleRepository.findDueForUpdate(
				now,
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
		request.setExtensionExpiresAt(now.plus(
				properties.extensionResponseTimeout()
		));
		request.setExtensionTargetTimeSlot(targetTimeSlot);
		outboxEventService.publish(new LunchExtensionRequestedPayload(
				request.getId(),
				offerId
		));
		return true;
	}
}
