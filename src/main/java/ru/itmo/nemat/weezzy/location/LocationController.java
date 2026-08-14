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
import ru.itmo.nemat.weezzy.location.dto.CreateLocationRequest;
import ru.itmo.nemat.weezzy.location.dto.LocationResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Validated
public class LocationController {
	private final LocationService service;

	@PostMapping
	public ResponseEntity<LocationResponse> create(
			@Valid @RequestBody CreateLocationRequest request
	) {
		Location location = service.create(request);
		return ResponseEntity
				.created(URI.create("/api/locations/" + location.getId()))
				.body(LocationResponse.from(location));
	}

	@GetMapping("/{id}")
	public ResponseEntity<LocationResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(LocationResponse.from(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<PageResponse<LocationResponse>> findAll(
			@RequestParam(required = false) UUID universityId,
			@RequestParam(required = false) LocationType type,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by("name").ascending().and(Sort.by("id").ascending())
		);
		return ResponseEntity.ok(PageResponse.from(
				service.findActive(universityId, type, pageable).map(LocationResponse::from)
		));
	}
}
