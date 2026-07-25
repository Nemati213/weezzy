package ru.itmo.nemat.weezzy.profile.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileGoalRepository extends JpaRepository<ProfileGoal, ProfileGoalId> {
	List<ProfileGoal> findAllByProfileId(UUID profileId);

	boolean existsByProfileIdAndGoalId(UUID profileId, UUID goalId);

	void deleteByProfileIdAndGoalId(UUID profileId, UUID goalId);
}
