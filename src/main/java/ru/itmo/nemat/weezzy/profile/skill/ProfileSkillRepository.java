package ru.itmo.nemat.weezzy.profile.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, ProfileSkillId> {
	List<ProfileSkill> findAllByProfileId(UUID profileId);

	boolean existsByProfileIdAndSkillId(UUID profileId, UUID skillId);

	void deleteByProfileIdAndSkillId(UUID profileId, UUID skillId);

	List<ProfileSkill> findAllByProfileIdIn(Collection<UUID> profileIds);

}
