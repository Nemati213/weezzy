package ru.itmo.nemat.weezzy.connection.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileMatchRepository extends JpaRepository<ProfileMatch, ProfileMatchId> {
	List<ProfileMatch> findByFirstProfileIdOrSecondProfileIdOrderByCreatedAtDesc(
			UUID firstProfileId,
			UUID secondProfileId
	);
}
