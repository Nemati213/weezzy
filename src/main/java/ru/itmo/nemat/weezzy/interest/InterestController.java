package ru.itmo.nemat.weezzy.interest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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
import ru.itmo.nemat.weezzy.interest.dto.CreateInterestRequest;
import ru.itmo.nemat.weezzy.interest.dto.InterestResponse;
import ru.itmo.nemat.weezzy.interest.dto.UpdateInterestRequest;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
@Validated
public class InterestController {
	private final InterestService service;

	@PostMapping
	public ResponseEntity<InterestResponse> createInterest(@Valid @RequestBody CreateInterestRequest request) {
		Interest interest = service.create(request);
		return ResponseEntity
				.created(URI.create("/api/interests/" + interest.getId()))
				.body(InterestResponse.from(interest));
	}

	@GetMapping("/{id}")
	public ResponseEntity<InterestResponse> getInterest(@PathVariable UUID id) {
		return ResponseEntity.ok(InterestResponse.from(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<PageResponse<InterestResponse>> getAllInterests(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by("name").ascending().and(Sort.by("id").ascending())
		);

		Page<InterestResponse> result = service.findAll(pageable)
				.map(InterestResponse::from);

		return ResponseEntity.ok(PageResponse.from(result));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<InterestResponse> updateInterest(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateInterestRequest request
	) {
		return ResponseEntity.ok(InterestResponse.from(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteInterest(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
