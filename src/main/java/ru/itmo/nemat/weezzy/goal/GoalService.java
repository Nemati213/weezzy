package ru.itmo.nemat.weezzy.goal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.onboarding.OnboardingService;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.goal.dto.CreateGoalRequest;
import ru.itmo.nemat.weezzy.goal.dto.UpdateGoalRequest;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {
	private final GoalRepository repository;
	private final OnboardingService onboardingService;

	@Transactional
	public Goal create(CreateGoalRequest request) {
		String normalizedCode = normalizeCode(request.code());
		String normalizedName = request.name().trim();
		ensureCodeIsUnique(normalizedCode, null);
		ensureNameIsUnique(normalizedName, null);

		Goal goal = new Goal();
		goal.setCode(normalizedCode);
		goal.setName(normalizedName);
		goal.setDescription(request.description());

		return repository.save(goal);
	}

	@Transactional(readOnly = true)
	public Goal findById(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new GoalNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public List<Goal> findAll() {
		return repository.findAll();
	}

	@Transactional
	public Goal update(UUID id, UpdateGoalRequest request) {
		Goal goal = findById(id);

		if (request.code() != null) {
			String normalizedCode = normalizeCode(request.code());
			ensureCodeIsUnique(normalizedCode, id);
			goal.setCode(normalizedCode);
		}
		if (request.name() != null) {
			String normalizedName = request.name().trim();
			ensureNameIsUnique(normalizedName, id);
			goal.setName(normalizedName);
		}
		if (request.description() != null) {
			goal.setDescription(request.description());
		}

		return repository.save(goal);
	}

	@Transactional
	public void delete(UUID id) {
		Goal goal = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new GoalNotFoundException(id));
		List<Profile> affectedProfiles = onboardingService.lockProfilesUsingGoal(id);
		repository.delete(goal);
		repository.flush();
		onboardingService.moveToDraftIfIncomplete(affectedProfiles);
	}

	private String normalizeCode(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}

	private void ensureCodeIsUnique(String code, UUID currentGoalId) {
		repository.findByCodeIgnoreCase(code)
				.filter(existing -> !existing.getId().equals(currentGoalId))
				.ifPresent(existing -> {
					throw new DuplicateGoalException(code);
				});
	}

	private void ensureNameIsUnique(String name, UUID currentGoalId) {
		repository.findByNameIgnoreCase(name)
				.filter(existing -> !existing.getId().equals(currentGoalId))
				.ifPresent(existing -> {
					throw new DuplicateGoalException(name);
				});
	}
}
