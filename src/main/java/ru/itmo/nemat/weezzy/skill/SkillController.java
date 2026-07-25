package ru.itmo.nemat.weezzy.skill;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.skill.dto.CreateSkillRequest;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {
	private final SkillService service;

	@PostMapping
	public ResponseEntity<SkillResponse> createSkill(@Valid @RequestBody CreateSkillRequest request) {
		Skill skill = service.create(request);
		return ResponseEntity
				.created(URI.create("/api/skills/" + skill.getId()))
				.body(SkillResponse.from(skill));
	}

	@GetMapping("/{id}")
	public ResponseEntity<SkillResponse> getSkill(@PathVariable UUID id) {
		return ResponseEntity.ok(SkillResponse.from(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<List<SkillResponse>> getAllSkills() {
		return ResponseEntity.ok(service.findAll().stream().map(SkillResponse::from).toList());
	}
}
