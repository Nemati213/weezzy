package ru.itmo.nemat.weezzy.outbox.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;
import ru.itmo.nemat.weezzy.outbox.OutboxEventStatus;
import ru.itmo.nemat.weezzy.outbox.exception.OutboxEventClaimException;
import ru.itmo.nemat.weezzy.outbox.exception.OutboxEventNotFoundException;
import ru.itmo.nemat.weezzy.outbox.handler.OutboxEventHandlerRegistry;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventProcessor {
	private final OutboxEventRepository repository;
	private final OutboxEventHandlerRegistry handlerRegistry;
	private final OutboxRetryPolicy retryPolicy;

	@Transactional
	public void process(UUID eventId, String workerId, LocalDateTime now) {
		OutboxEvent event = findClaimedEvent(eventId, workerId);
		handlerRegistry.getHandler(event.getEventType()).handle(event);
		event.markProcessed(now);
	}

	@Transactional
	public void recordFailure(
			UUID eventId,
			String workerId,
			Exception exception,
			LocalDateTime now
	) {
		OutboxEvent event = findClaimedEvent(eventId, workerId);
		String error = errorMessage(exception);

		if (retryPolicy.attemptsExhausted(event.getAttemptCount())) {
			event.markFailed(error, now);
			return;
		}

		event.scheduleRetry(
				error,
				retryPolicy.nextAttemptAt(event.getAttemptCount(), now),
				now
		);
	}

	private OutboxEvent findClaimedEvent(UUID eventId, String workerId) {
		OutboxEvent event = repository.findByIdForUpdate(eventId)
				.orElseThrow(() -> new OutboxEventNotFoundException(eventId));
		if (event.getStatus() != OutboxEventStatus.PROCESSING
				|| !workerId.equals(event.getLockedBy())) {
			throw new OutboxEventClaimException(
					eventId,
					event.getStatus(),
					event.getLockedBy(),
					workerId
			);
		}
		return event;
	}

	private String errorMessage(Exception exception) {
		String message = exception.getMessage();
		String error = exception.getClass().getSimpleName()
				+ (message == null || message.isBlank() ? "" : ": " + message);
		return error.length() <= OutboxEvent.LAST_ERROR_MAX_LENGTH
				? error
				: error.substring(0, OutboxEvent.LAST_ERROR_MAX_LENGTH);
	}
}
