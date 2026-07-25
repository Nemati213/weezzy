package ru.itmo.nemat.weezzy.profile.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileSkillService {
	private final ProfileService profileService;
	private final SkillService skillService;
	private final ProfileSkillRepository profileSkillRepository;

	@Transactional
	public Skill addSkill(UUID profileId, UUID skillId) {
		profileService.findById(profileId);
		Skill skill = skillService.findById(skillId);

		if (profileSkillRepository.existsByProfileIdAndSkillId(profileId, skillId)) {
			throw new ProfileSkillAlreadyExistsException(profileId, skillId);
		}

		ProfileSkill profileSkill = new ProfileSkill();
		profileSkill.setProfileId(profileId);
		profileSkill.setSkillId(skillId);
		profileSkillRepository.save(profileSkill);

		return skill;
	}

	@Transactional(readOnly = true)
	public List<Skill> findSkills(UUID profileId) {
		profileService.findById(profileId);

		return profileSkillRepository.findAllByProfileId(profileId).stream()
				.map(ProfileSkill::getSkillId)
				.map(skillService::findById)
				.toList();
	}

	@Transactional
	public void removeSkill(UUID profileId, UUID skillId) {
		profileService.findById(profileId);
		skillService.findById(skillId);

		if (!profileSkillRepository.existsByProfileIdAndSkillId(profileId, skillId)) {
			throw new ProfileSkillNotFoundException(profileId, skillId);
		}

		profileSkillRepository.deleteByProfileIdAndSkillId(profileId, skillId);
	}
}
