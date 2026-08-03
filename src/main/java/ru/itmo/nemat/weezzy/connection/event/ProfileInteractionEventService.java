package ru.itmo.nemat.weezzy.connection.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileInteractionEventService {
	private final ProfileInteractionEventRepository repository;

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(
			UUID sourceProfileId,
			UUID targetProfileId,
			ProfileInteractionEventType eventType
	) {
		repository.save(newEvent(sourceProfileId, targetProfileId, eventType));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void recordAll(
			UUID sourceProfileId,
			List<UUID> targetProfileIds,
			ProfileInteractionEventType eventType
	) {
		repository.saveAll(targetProfileIds.stream()
				.map(targetProfileId -> newEvent(
						sourceProfileId,
						targetProfileId,
						eventType
				))
				.toList());
	}

	private ProfileInteractionEvent newEvent(
			UUID sourceProfileId,
			UUID targetProfileId,
			ProfileInteractionEventType eventType
	) {
		ProfileInteractionEvent event = new ProfileInteractionEvent();
		event.setSourceProfileId(sourceProfileId);
		event.setTargetProfileId(targetProfileId);
		event.setEventType(eventType);
		return event;
	}
}
