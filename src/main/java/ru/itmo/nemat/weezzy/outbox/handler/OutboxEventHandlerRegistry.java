package ru.itmo.nemat.weezzy.outbox.handler;

import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.exception.DuplicateOutboxEventHandlerException;
import ru.itmo.nemat.weezzy.outbox.exception.OutboxEventHandlerNotFoundException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboxEventHandlerRegistry {
	private final Map<OutboxEventType, OutboxEventHandler> handlers;

	public OutboxEventHandlerRegistry(List<OutboxEventHandler> handlerList) {
		Map<OutboxEventType, OutboxEventHandler> handlersByType =
				new EnumMap<>(OutboxEventType.class);

		for (OutboxEventHandler handler : handlerList) {
			OutboxEventHandler previous = handlersByType.putIfAbsent(
					handler.supportedType(),
					handler
			);
			if (previous != null) {
				throw new DuplicateOutboxEventHandlerException(handler.supportedType());
			}
		}

		this.handlers = Map.copyOf(handlersByType);
	}

	public OutboxEventHandler getHandler(OutboxEventType eventType) {
		OutboxEventHandler handler = handlers.get(eventType);
		if (handler == null) {
			throw new OutboxEventHandlerNotFoundException(eventType);
		}
		return handler;
	}
}
