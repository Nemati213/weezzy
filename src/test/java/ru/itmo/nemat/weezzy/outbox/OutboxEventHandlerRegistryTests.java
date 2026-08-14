package ru.itmo.nemat.weezzy.outbox;

import org.junit.jupiter.api.Test;
import ru.itmo.nemat.weezzy.outbox.exception.DuplicateOutboxEventHandlerException;
import ru.itmo.nemat.weezzy.outbox.exception.OutboxEventHandlerNotFoundException;
import ru.itmo.nemat.weezzy.outbox.handler.OutboxEventHandler;
import ru.itmo.nemat.weezzy.outbox.handler.OutboxEventHandlerRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventHandlerRegistryTests {
	@Test
	void rejectsDuplicateHandlersForSameEventType() {
		OutboxEventHandler first = handler(OutboxEventType.PROFILE_LIKED);
		OutboxEventHandler second = handler(OutboxEventType.PROFILE_LIKED);

		assertThatThrownBy(() -> new OutboxEventHandlerRegistry(List.of(first, second)))
				.isInstanceOf(DuplicateOutboxEventHandlerException.class)
				.hasMessageContaining("PROFILE_LIKED");
	}

	@Test
	void rejectsMissingHandler() {
		OutboxEventHandlerRegistry registry = new OutboxEventHandlerRegistry(List.of());

		assertThatThrownBy(() -> registry.getHandler(OutboxEventType.REPORT_DECIDED))
				.isInstanceOf(OutboxEventHandlerNotFoundException.class)
				.hasMessageContaining("REPORT_DECIDED");
	}

	private OutboxEventHandler handler(OutboxEventType eventType) {
		return new OutboxEventHandler() {
			@Override
			public OutboxEventType supportedType() {
				return eventType;
			}

			@Override
			public void handle(OutboxEvent event) {
			}
		};
	}
}
