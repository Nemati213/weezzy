package ru.itmo.nemat.weezzy.goal;

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
import ru.itmo.nemat.weezzy.goal.dto.CreateGoalRequest;
import ru.itmo.nemat.weezzy.goal.dto.GoalResponse;
import ru.itmo.nemat.weezzy.goal.dto.UpdateGoalRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
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
	public ResponseEntity<List<GoalResponse>> getAllGoals() {
		return ResponseEntity.ok(service.findAll().stream().map(GoalResponse::from).toList());
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
