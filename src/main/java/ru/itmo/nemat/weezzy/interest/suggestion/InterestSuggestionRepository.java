package ru.itmo.nemat.weezzy.interest.suggestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InterestSuggestionRepository extends JpaRepository<InterestSuggestion, UUID> {
	void deleteAllBySuggestedById(UUID userId);

	@Modifying
	@Query("""
			UPDATE InterestSuggestion suggestion
			SET suggestion.reviewedBy = NULL
			WHERE suggestion.reviewedBy.id = :userId
			""")
	int clearReviewer(@Param("userId") UUID userId);

	List<InterestSuggestion> findBySuggestedByIdOrderByCreatedAtDesc(UUID userId);

	Page<InterestSuggestion> findByStatusOrderByCreatedAtAsc(
			InterestSuggestionStatus status,
			Pageable pageable
	);

	boolean existsBySuggestedByIdAndNameIgnoreCaseAndStatus(
			UUID userId,
			String name,
			InterestSuggestionStatus status
	);
}
