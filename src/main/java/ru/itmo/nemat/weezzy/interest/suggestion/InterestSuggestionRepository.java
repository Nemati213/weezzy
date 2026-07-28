package ru.itmo.nemat.weezzy.interest.suggestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterestSuggestionRepository extends JpaRepository<InterestSuggestion, UUID> {
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
