package ru.itmo.nemat.weezzy.connection.vote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileVoteRepository extends JpaRepository<ProfileVote, ProfileVoteId> {
	Optional<ProfileVote> findBySourceProfileIdAndTargetProfileId(UUID sourceProfileId, UUID targetProfileId);

	List<ProfileVote> findBySourceProfileId(UUID sourceProfileId);
}
