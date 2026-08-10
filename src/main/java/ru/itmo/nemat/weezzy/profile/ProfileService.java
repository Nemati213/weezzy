package ru.itmo.nemat.weezzy.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.onboarding.OnboardingService;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserService;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
	private final ProfileRepository profileRepository;
	private final UserService userService;
	private final OnboardingService onboardingService;

	@Transactional
	public Profile create(CreateProfileRequest request) {
		return profileRepository.save(buildProfile(request));
	}

	@Transactional
	public Profile createForUser(UUID userId, CreateProfileRequest request) {
		if (profileRepository.existsByUserId(userId)) {
			throw new ProfileAlreadyExistsForUserException(userId);
		}

		User user = userService.findById(userId);
		Profile profile = new Profile();
		profile.setUser(user);
		copyCreateFields(profile, request);

		return profileRepository.save(profile);
	}

	@Transactional(readOnly = true)
	public List<Profile> findAllByIds(Collection<UUID> profileIds) {
		return profileRepository.findAllById(profileIds);
	}

	private Profile buildProfile(CreateProfileRequest request) {
		Profile profile = new Profile();
		copyCreateFields(profile, request);

		return profile;
	}

	private void copyCreateFields(Profile profile, CreateProfileRequest request) {
		profile.setDisplayName(request.displayName());
		profile.setBio(request.bio());
		profile.setTelegram(request.telegram());
		profile.setFaculty(request.faculty());
		profile.setStudyProgram(request.studyProgram());
		profile.setCourse(request.course());
	}

	public Profile findById(UUID id) {
		return profileRepository.findById(id)
				.orElseThrow(() -> new ProfileNotFoundException(id));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Profile findByIdForUpdate(UUID id) {
		return profileRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ProfileNotFoundException(id));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Profile findByUserIdForUpdate(UUID userId) {
		return profileRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new ProfileNotFoundException(userId));
	}

	public Profile findByUserId(UUID userId) {
		return profileRepository.findByUserId(userId).orElseThrow(() -> new ProfileNotFoundException(userId));
	}

	@Transactional(readOnly = true)
	public Optional<Profile> findOptionalByUserId(UUID userId) {
		return profileRepository.findByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<Profile> findAll() {
		return profileRepository.findAllByStatusNot(ProfileStatus.DELETED);
	}

	@Transactional(readOnly = true)
	public Page<Profile> findAll(Pageable pageable) {
		return profileRepository.findAllByStatusNot(ProfileStatus.DELETED, pageable);
	}

	@Transactional(readOnly = true)
	public void ensureActive(UUID profileId) {
		Profile profile = findById(profileId);
		if (profile.getStatus() == ProfileStatus.DELETED) {
			throw new DeletedProfileInteractionException(profileId);
		}
	}

	@Transactional
	public Profile update(UUID id, UpdateProfileRequest request) {
		Profile profile = findByIdForUpdate(id);
		applyUpdate(profile, request);

		return profileRepository.save(profile);
	}

	@Transactional
	public Profile updateForUser(UUID userId, UpdateProfileRequest request) {
		Profile profile = profileRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new ProfileNotFoundException(userId));
		applyUpdate(profile, request);

		return profileRepository.save(profile);
	}

	private void applyUpdate(Profile profile, UpdateProfileRequest request) {
		if (request.status() == ProfileStatus.DELETED) {
			throw new DeletedProfileInteractionException(profile.getId());
		}
		if (request.displayName() != null) {
			profile.setDisplayName(request.displayName());
		}
		if (request.bio() != null) {
			profile.setBio(request.bio());
		}
		if (request.telegram() != null) {
			profile.setTelegram(request.telegram());
		}
		if (request.faculty() != null) {
			profile.setFaculty(request.faculty());
		}
		if (request.studyProgram() != null) {
			profile.setStudyProgram(request.studyProgram());
		}
		if (request.course() != null) {
			profile.setCourse(request.course());
		}

		if (request.status() == ProfileStatus.ACTIVE) {
			onboardingService.validateActivationAllowed(profile);
			profile.setStatus(request.status());
			return;
		}
		if (request.status() != null) {
			profile.setStatus(request.status());
		}
		onboardingService.moveToDraftIfIncomplete(profile);
	}
}
