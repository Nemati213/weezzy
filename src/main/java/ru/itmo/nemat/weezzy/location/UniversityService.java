package ru.itmo.nemat.weezzy.location;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.location.dto.CreateUniversityRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UniversityService {
	private final UniversityRepository repository;

	@Transactional
	public University create(CreateUniversityRequest request) {
		String name = request.name().trim();
		String city = request.city().trim();
		repository.findByNameIgnoreCaseAndCityIgnoreCase(name, city)
				.ifPresent(university -> {
					throw new DuplicateUniversityException(name, city);
				});

		University university = new University();
		university.setName(name);
		university.setCity(city);
		return repository.save(university);
	}

	@Transactional(readOnly = true)
	public University findById(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new UniversityNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public Page<University> findAll(Pageable pageable) {
		return repository.findAll(pageable);
	}
}
