package ru.itmo.nemat.weezzy.profile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {
	private final ProfileService service;
	private final ProfileSkillService profileSkillService;

	@PostMapping
	public ResponseEntity<ProfileResponse> createProfile(@Valid @RequestBody CreateProfileRequest request) {
		Profile profile = service.create(request);

		return ResponseEntity
				.created(URI.create("/api/profiles/" + profile.getId()))
				.body(ProfileResponse.from(profile));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProfileResponse> getProfile(@PathVariable UUID id) {
		return ResponseEntity.ok(ProfileResponse.from(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<List<ProfileResponse>> getAllProfiles() {
		return ResponseEntity.ok(service.findAll().stream().map(ProfileResponse::from).toList());
	}

	@PatchMapping("/{id}")
	public ResponseEntity<ProfileResponse> updateProfile(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(ProfileResponse.from(service.update(id, request)));
	}

	@PostMapping("/{profileId}/skills/{skillId}")
	public ResponseEntity<SkillResponse> addSkill(@PathVariable UUID profileId, @PathVariable UUID skillId) {
		Skill skill = profileSkillService.addSkill(profileId, skillId);
		return ResponseEntity
				.created(URI.create("/api/profiles/" + profileId + "/skills/" + skillId))
				.body(SkillResponse.from(skill));
	}

	@GetMapping("/{profileId}/skills")
	public ResponseEntity<List<SkillResponse>> getSkills(@PathVariable UUID profileId) {
		return ResponseEntity.ok(profileSkillService.findSkills(profileId).stream()
				.map(SkillResponse::from)
				.toList());
	}

	@DeleteMapping("/{profileId}/skills/{skillId}")
	public ResponseEntity<Void> removeSkill(@PathVariable UUID profileId, @PathVariable UUID skillId) {
		profileSkillService.removeSkill(profileId, skillId);
		return ResponseEntity.noContent().build();
	}
}
