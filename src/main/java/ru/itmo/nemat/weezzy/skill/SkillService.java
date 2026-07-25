package ru.itmo.nemat.weezzy.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.skill.dto.CreateSkillRequest;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillService {
	private final SkillRepository repository;

	@Transactional
	public Skill create(CreateSkillRequest request) {
		String normalizedName = normalizeName(request.name());
		repository.findByNameIgnoreCase(normalizedName).ifPresent(skill -> {
			throw new DuplicateSkillException(normalizedName);
		});

		Skill skill = new Skill();
		skill.setName(normalizedName);
		skill.setDescription(request.description());

		return repository.save(skill);
	}

	@Transactional(readOnly = true)
	public Skill findById(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new SkillNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public List<Skill> findAll() {
		return repository.findAll();
	}

	private String normalizeName(String name) {
		return name.trim();
	}
}
