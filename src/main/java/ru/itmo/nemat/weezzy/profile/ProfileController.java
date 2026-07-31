package ru.itmo.nemat.weezzy.profile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.dto.GoalResponse;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.dto.InterestResponse;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalService;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
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
	private final ProfileAccessService accessService;
	private final ProfileSkillService profileSkillService;
	private final ProfileInterestService profileInterestService;
	private final ProfileGoalService profileGoalService;

	@PostMapping
	public ResponseEntity<ProfileResponse> createProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateProfileRequest request
	) {
		Profile profile = service.createForUser(authenticatedUser.id(), request);

		return ResponseEntity
				.created(URI.create("/api/profiles/" + profile.getId()))
				.body(ProfileResponse.withContact(profile));
	}

	@GetMapping("/me")
	public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser) {
		return ResponseEntity.ok(ProfileResponse.withContact(service.findByUserId(authenticatedUser.id())));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProfileResponse> getProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID id
	) {
		return ResponseEntity.ok(accessService.findByIdForUser(authenticatedUser.id(), id));
	}

	@GetMapping
	public ResponseEntity<List<ProfileResponse>> getAllProfiles() {
		return ResponseEntity.ok(service.findAll().stream().map(ProfileResponse::from).toList());
	}

	@PatchMapping("/me")
	public ResponseEntity<ProfileResponse> updateProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody UpdateProfileRequest request
	) {
		return ResponseEntity.ok(ProfileResponse.withContact(service.updateForUser(authenticatedUser.id(), request)));
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
