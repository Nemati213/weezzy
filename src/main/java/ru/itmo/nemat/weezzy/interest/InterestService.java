package ru.itmo.nemat.weezzy.interest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.interest.dto.CreateInterestRequest;
import ru.itmo.nemat.weezzy.interest.dto.UpdateInterestRequest;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterestService {
	private final InterestRepository repository;

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
		repository.delete(findById(id));
	}
}
