package ru.itmo.nemat.weezzy.skill.suggestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SkillSuggestionRepository extends JpaRepository<SkillSuggestion, UUID> {
	void deleteAllBySuggestedById(UUID userId);

	@Modifying
	@Query("""
			UPDATE SkillSuggestion suggestion
			SET suggestion.reviewedBy = NULL
			WHERE suggestion.reviewedBy.id = :userId
			""")
	int clearReviewer(@Param("userId") UUID userId);

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
