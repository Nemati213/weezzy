package ru.itmo.nemat.weezzy.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.skill.dto.CreateSkillRequest;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
	public List<Skill> findAllByIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		List<Skill> skills = repository.findAllById(ids);
		Set<UUID> foundIds = skills.stream().map(Skill::getId).collect(Collectors.toSet());
		for (UUID id : ids) {
			if (!foundIds.contains(id)) {
				throw new SkillNotFoundException(id);
			}
		}
		return skills;
	}

	@Transactional(readOnly = true)
	public List<Skill> findAll() {
		return repository.findAll();
	}

	@Transactional(readOnly = true)
	public Page<Skill> findAll(Pageable pageable) {
		return repository.findAll(pageable);
	}

	private String normalizeName(String name) {
		return name.trim();
	}
}
