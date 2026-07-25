package ru.itmo.nemat.weezzy.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
	Optional<Skill> findByNameIgnoreCase(String name);
}
