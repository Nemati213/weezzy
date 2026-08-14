package ru.itmo.nemat.weezzy.outbox;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.outbox.exception.InvalidOutboxEventPayloadException;
import ru.itmo.nemat.weezzy.outbox.payload.OutboxPayload;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
	private final OutboxEventRepository eventRepository;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	@Transactional(propagation = Propagation.MANDATORY)
	public OutboxEvent publish(OutboxPayload payload) {
		Objects.requireNonNull(payload, "Outbox payload must not be null");
		var violations = validator.validate(payload);
		if (!violations.isEmpty()) {
			throw new InvalidOutboxEventPayloadException(
					null,
					payload.eventType(),
					new IllegalArgumentException(violations.toString())
			);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> payloadMap = objectMapper.convertValue(payload, Map.class);

		OutboxEvent outboxEvent = new OutboxEvent();
		outboxEvent.setEventType(payload.eventType());
		outboxEvent.setPayload(payloadMap);
		return eventRepository.save(outboxEvent);
	}
}
