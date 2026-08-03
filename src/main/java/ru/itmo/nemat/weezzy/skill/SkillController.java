package ru.itmo.nemat.weezzy.skill;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.skill.dto.CreateSkillRequest;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Validated
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
	public ResponseEntity<PageResponse<SkillResponse>> getAllSkills(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by("name").ascending().and(Sort.by("id").ascending())
		);

		Page<SkillResponse> result = service.findAll(pageable)
				.map(SkillResponse::from);

		return ResponseEntity.ok(PageResponse.from(result));
	}
}
