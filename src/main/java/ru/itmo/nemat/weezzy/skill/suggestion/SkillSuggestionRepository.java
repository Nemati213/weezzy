package ru.itmo.nemat.weezzy.skill.suggestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillSuggestionRepository extends JpaRepository<SkillSuggestion, UUID> {
	List<SkillSuggestion> findBySuggestedByIdOrderByCreatedAtDesc(UUID userId);

	Page<SkillSuggestion> findByStatusOrderByCreatedAtAsc(
			SkillSuggestionStatus status,
			Pageable pageable
	);

	boolean existsBySuggestedByIdAndNameIgnoreCaseAndStatus(
			UUID userId,
			String name,
			SkillSuggestionStatus status
	);
}
