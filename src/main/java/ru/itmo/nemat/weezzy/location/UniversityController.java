package ru.itmo.nemat.weezzy.location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.location.dto.CreateUniversityRequest;
import ru.itmo.nemat.weezzy.location.dto.UniversityResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/universities")
@RequiredArgsConstructor
@Validated
public class UniversityController {
	private final UniversityService service;

	@PostMapping
	public ResponseEntity<UniversityResponse> create(
			@Valid @RequestBody CreateUniversityRequest request
	) {
		University university = service.create(request);
		return ResponseEntity
				.created(URI.create("/api/universities/" + university.getId()))
				.body(UniversityResponse.from(university));
	}

	@GetMapping("/{id}")
	public ResponseEntity<UniversityResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(UniversityResponse.from(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<PageResponse<UniversityResponse>> findAll(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by("city").ascending()
						.and(Sort.by("name").ascending())
						.and(Sort.by("id").ascending())
		);
		return ResponseEntity.ok(PageResponse.from(
				service.findAll(pageable).map(UniversityResponse::from)
		));
	}
}
