package ru.itmo.nemat.weezzy.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileInterestRepository extends JpaRepository<ProfileInterest, ProfileInterestId> {
	List<ProfileInterest> findAllByProfileId(UUID profileId);

	boolean existsByProfileIdAndInterestId(UUID profileId, UUID interestId);

	void deleteByProfileIdAndInterestId(UUID profileId, UUID interestId);
}
