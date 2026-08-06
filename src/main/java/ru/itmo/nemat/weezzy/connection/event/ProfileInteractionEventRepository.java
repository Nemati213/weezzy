package ru.itmo.nemat.weezzy.connection.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileInteractionEventRepository
		extends JpaRepository<ProfileInteractionEvent, UUID> {
	void deleteAllBySourceProfileIdOrTargetProfileId(
			UUID sourceProfileId,
			UUID targetProfileId
	);

	List<ProfileInteractionEvent> findAllBySourceProfileIdOrderByOccurredAtAscIdAsc(
			UUID sourceProfileId
	);
}
