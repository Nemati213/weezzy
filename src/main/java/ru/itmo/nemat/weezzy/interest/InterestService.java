package ru.itmo.nemat.weezzy.interest;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.onboarding.OnboardingService;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.interest.dto.CreateInterestRequest;
import ru.itmo.nemat.weezzy.interest.dto.UpdateInterestRequest;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterestService {
	private final InterestRepository repository;
	private final OnboardingService onboardingService;

	@Transactional
	public Interest create(CreateInterestRequest request) {
		String normalizedName = request.name().trim();
		repository.findByNameIgnoreCase(normalizedName).ifPresent(interest -> {
			throw new DuplicateInterestException(normalizedName);
		});

		Interest interest = new Interest();
		interest.setName(normalizedName);
		interest.setDescription(request.description());

		return repository.save(interest);
	}

	@Transactional(readOnly = true)
	public Interest findById(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new InterestNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public List<Interest> findAll() {
		return repository.findAll();
	}
	@Transactional(readOnly = true)
	public Page<Interest> findAll(Pageable pageable) {
		return repository.findAll(pageable);
	}

	@Transactional
	public Interest update(UUID id, UpdateInterestRequest request) {
		Interest interest = findById(id);

		if (request.name() != null) {
			String normalizedName = request.name().trim();
			repository.findByNameIgnoreCase(normalizedName)
					.filter(existing -> !existing.getId().equals(id))
					.ifPresent(existing -> {
						throw new DuplicateInterestException(normalizedName);
					});
			interest.setName(normalizedName);
		}
		if (request.description() != null) {
			interest.setDescription(request.description());
		}

		return repository.save(interest);
	}

	@Transactional
	public void delete(UUID id) {
		Interest interest = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new InterestNotFoundException(id));
		List<Profile> affectedProfiles = onboardingService.lockProfilesUsingInterest(id);
		repository.delete(interest);
		repository.flush();
		onboardingService.moveToDraftIfIncomplete(affectedProfiles);
	}
}
