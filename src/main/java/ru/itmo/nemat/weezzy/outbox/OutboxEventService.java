package ru.itmo.nemat.weezzy.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.outbox.payload.OutboxPayload;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
	private final OutboxEventRepository eventRepository;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.MANDATORY)
	public OutboxEvent publish(OutboxPayload payload) {
		@SuppressWarnings("unchecked")
		Map<String, Object> payloadMap = objectMapper.convertValue(payload, Map.class);

		OutboxEvent outboxEvent = new OutboxEvent();
		outboxEvent.setEventType(payload.eventType());
		outboxEvent.setPayload(payloadMap);
		return eventRepository.save(outboxEvent);
	}
}
