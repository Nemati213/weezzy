package ru.itmo.nemat.weezzy.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
	private final ProfileRepository profileRepository;
	private final UserService userService;

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

	public Profile findByUserId(UUID userId) {
		return profileRepository.findByUserId(userId).orElseThrow(() -> new ProfileNotFoundException(userId));
	}

	public List<Profile> findAll() {
		return profileRepository.findAll();
	}

	@Transactional
	public Profile update(UUID id, UpdateProfileRequest request) {
		Profile profile = findById(id);
		copyUpdateFields(profile, request);

		return profileRepository.save(profile);
	}

	@Transactional
	public Profile updateForUser(UUID userId, UpdateProfileRequest request) {
		Profile profile = findByUserId(userId);
		copyUpdateFields(profile, request);

		return profileRepository.save(profile);
	}

	private void copyUpdateFields(Profile profile, UpdateProfileRequest request) {
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
		if (request.status() != null) {
			profile.setStatus(request.status());
		}
	}
}
