package ru.itmo.nemat.weezzy.outbox.handler;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.exception.InvalidOutboxEventPayloadException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxPayloadMapper {
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public <T> T read(OutboxEvent event, Class<T> payloadType) {
		try {
			T payload = objectMapper.convertValue(event.getPayload(), payloadType);
			var violations = validator.validate(payload);
			if (!violations.isEmpty()) {
				throw new IllegalArgumentException(violations.toString());
			}
			return payload;
		} catch (RuntimeException exception) {
			throw new InvalidOutboxEventPayloadException(
					event.getId(),
					event.getEventType(),
					exception
			);
		}
	}
}
