package ru.itmo.nemat.weezzy.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
	private final ProfileRepository profileRepository;

	@Transactional
	public Profile create(CreateProfileRequest request) {
		Profile profile = new Profile();
		profile.setDisplayName(request.displayName());
		profile.setBio(request.bio());
		profile.setTelegram(request.telegram());
		profile.setFaculty(request.faculty());
		profile.setStudyProgram(request.studyProgram());
		profile.setCourse(request.course());

		return profileRepository.save(profile);
	}

	public Profile findById(UUID id) {
		return profileRepository.findById(id)
				.orElseThrow(() -> new ProfileNotFoundException(id));
	}

	public List<Profile> findAll() {
		return profileRepository.findAll();
	}

	@Transactional
	public Profile update(UUID id, UpdateProfileRequest request) {
		Profile profile = findById(id);
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
		return profileRepository.save(profile);
	}
}
