package ru.itmo.nemat.weezzy.location;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.location.dto.CreateLocationRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {
	private final LocationRepository locationRepository;
	private final UniversityRepository universityRepository;

	@Transactional
	public Location create(CreateLocationRequest request) {
		University university = universityRepository.findById(request.universityId())
				.orElseThrow(() -> new UniversityNotFoundException(request.universityId()));
		String name = request.name().trim();
		String address = request.address().trim();

		if (locationRepository.existsByUniversityIdAndNameIgnoreCaseAndAddressIgnoreCase(
				university.getId(),
				name,
				address
		)) {
			throw new DuplicateLocationException(name, address);
		}

		Location location = new Location();
		location.setUniversity(university);
		location.setType(request.type());
		location.setName(name);
		location.setAddress(address);
		location.setDescription(normalizeNullable(request.description()));
		return locationRepository.save(location);
	}

	@Transactional(readOnly = true)
	public Location findById(UUID id) {
		return locationRepository.findByIdAndIsActiveTrue(id)
				.orElseThrow(() -> new LocationNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public Page<Location> findActive(
			UUID universityId,
			LocationType type,
			Pageable pageable
	) {
		return locationRepository.findActive(universityId, type, pageable);
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
