package ru.itmo.nemat.weezzy.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.dto.GoalResponse;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.dto.InterestResponse;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalService;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestService;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoService;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@Validated
public class ProfileController {
	private final ProfileService service;
	private final ProfileAccessService accessService;
	private final ProfileSkillService profileSkillService;
	private final ProfileInterestService profileInterestService;
	private final ProfileGoalService profileGoalService;
	private final ProfilePhotoService profilePhotoService;

	@PostMapping
	public ResponseEntity<ProfileResponse> createProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateProfileRequest request
	) {
		Profile profile = service.createForUser(authenticatedUser.id(), request);

		return ResponseEntity
				.created(URI.create("/api/profiles/" + profile.getId()))
				.body(ProfileResponse.withContact(profile, List.of()));
	}

	@GetMapping("/me")
	public ResponseEntity<ProfileResponse> getProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		Profile profile = service.findByUserId(authenticatedUser.id());
		return ResponseEntity.ok(ProfileResponse.withContact(
				profile,
				profilePhotoService.findReadyPhotos(profile.getId())
		));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProfileResponse> getProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID id
	) {
		return ResponseEntity.ok(accessService.findByIdForUser(authenticatedUser.id(), id));
	}

	@GetMapping
	public ResponseEntity<PageResponse<ProfileResponse>> getAllProfiles(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by("createdAt").descending().and(Sort.by("id").descending())
		);

		Page<Profile> profiles = service.findAll(pageable);
		Map<UUID, List<ProfilePhotoResponse>> photosByProfileId =
				profilePhotoService.findReadyPhotosByProfileIds(
						profiles.stream().map(Profile::getId).toList()
				);
		return ResponseEntity.ok(PageResponse.from(profiles.map(profile ->
				ProfileResponse.from(
						profile,
						photosByProfileId.getOrDefault(profile.getId(), List.of())
				))));
	}

	@PatchMapping("/me")
	public ResponseEntity<ProfileResponse> updateProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody UpdateProfileRequest request
	) {
		Profile profile = service.updateForUser(authenticatedUser.id(), request);
		return ResponseEntity.ok(ProfileResponse.withContact(
				profile,
				profilePhotoService.findReadyPhotos(profile.getId())
		));
	}

	@PostMapping("/me/skills/{skillId}")
	public ResponseEntity<SkillResponse> addSkill(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID skillId
	) {
		UUID profileId = currentProfileId(authenticatedUser);
		Skill skill = profileSkillService.addSkill(profileId, skillId);
		return ResponseEntity
				.created(URI.create("/api/profiles/me/skills/" + skillId))
				.body(SkillResponse.from(skill));
	}

	@GetMapping("/me/skills")
	public ResponseEntity<List<SkillResponse>> getSkills(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(profileSkillService.findSkills(currentProfileId(authenticatedUser)).stream()
				.map(SkillResponse::from)
				.toList());
	}

	@DeleteMapping("/me/skills/{skillId}")
	public ResponseEntity<Void> removeSkill(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID skillId
	) {
		profileSkillService.removeSkill(currentProfileId(authenticatedUser), skillId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/me/interests/{interestId}")
	public ResponseEntity<InterestResponse> addInterest(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID interestId
	) {
		UUID profileId = currentProfileId(authenticatedUser);
		Interest interest = profileInterestService.addInterest(profileId, interestId);
		return ResponseEntity
				.created(URI.create("/api/profiles/me/interests/" + interestId))
				.body(InterestResponse.from(interest));
	}

	@GetMapping("/me/interests")
	public ResponseEntity<List<InterestResponse>> getInterests(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(profileInterestService.findInterests(currentProfileId(authenticatedUser)).stream()
				.map(InterestResponse::from)
				.toList());
	}

	@DeleteMapping("/me/interests/{interestId}")
	public ResponseEntity<Void> removeInterest(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID interestId
	) {
		profileInterestService.removeInterest(currentProfileId(authenticatedUser), interestId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/me/goals/{goalId}")
	public ResponseEntity<GoalResponse> addGoal(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID goalId
	) {
		UUID profileId = currentProfileId(authenticatedUser);
		Goal goal = profileGoalService.addGoal(profileId, goalId);
		return ResponseEntity
				.created(URI.create("/api/profiles/me/goals/" + goalId))
				.body(GoalResponse.from(goal));
	}

	@GetMapping("/me/goals")
	public ResponseEntity<List<GoalResponse>> getGoals(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(profileGoalService.findGoals(currentProfileId(authenticatedUser)).stream()
				.map(GoalResponse::from)
				.toList());
	}

	@DeleteMapping("/me/goals/{goalId}")
	public ResponseEntity<Void> removeGoal(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID goalId
	) {
		profileGoalService.removeGoal(currentProfileId(authenticatedUser), goalId);
		return ResponseEntity.noContent().build();
	}

	private UUID currentProfileId(JwtAuthenticatedUser authenticatedUser) {
		return service.findByUserId(authenticatedUser.id()).getId();
	}
}
