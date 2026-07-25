package ru.itmo.nemat.weezzy.interest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.interest.dto.CreateInterestRequest;
import ru.itmo.nemat.weezzy.interest.dto.InterestResponse;
import ru.itmo.nemat.weezzy.interest.dto.UpdateInterestRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
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
	public ResponseEntity<List<InterestResponse>> getAllInterests() {
		return ResponseEntity.ok(service.findAll().stream().map(InterestResponse::from).toList());
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
