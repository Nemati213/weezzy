package ru.itmo.nemat.weezzy.goal;

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
import ru.itmo.nemat.weezzy.goal.dto.CreateGoalRequest;
import ru.itmo.nemat.weezzy.goal.dto.GoalResponse;
import ru.itmo.nemat.weezzy.goal.dto.UpdateGoalRequest;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Validated
public class GoalController {
	private final GoalService service;

	@PostMapping
	public ResponseEntity<GoalResponse> createGoal(@Valid @RequestBody CreateGoalRequest request) {
		Goal goal = service.create(request);
		return ResponseEntity
				.created(URI.create("/api/goals/" + goal.getId()))
				.body(GoalResponse.from(goal));
	}

	@GetMapping("/{id}")
	public ResponseEntity<GoalResponse> getGoal(@PathVariable UUID id) {
		return ResponseEntity.ok(GoalResponse.from(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<PageResponse<GoalResponse>> getAllGoals(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by("name").ascending().and(Sort.by("id").ascending())
		);

		Page<GoalResponse> goals = service.findAll(pageable)
				.map(GoalResponse::from);

		return ResponseEntity.ok(PageResponse.from(goals));

	}

	@PatchMapping("/{id}")
	public ResponseEntity<GoalResponse> updateGoal(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateGoalRequest request
	) {
		return ResponseEntity.ok(GoalResponse.from(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteGoal(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
