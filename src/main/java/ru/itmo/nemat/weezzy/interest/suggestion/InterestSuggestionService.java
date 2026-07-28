package ru.itmo.nemat.weezzy.interest.suggestion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.interest.DuplicateInterestException;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.InterestRepository;
import ru.itmo.nemat.weezzy.interest.suggestion.dto.CreateInterestSuggestionRequest;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterestSuggestionService {
	private final InterestSuggestionRepository interestSuggestionRepository;
	private final InterestRepository interestRepository;
	private final UserService userService;

	@Transactional
	public InterestSuggestion create(UUID userId, CreateInterestSuggestionRequest request) {
		String normalizedName = normalizeName(request.name());
		if (interestRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
			throw new DuplicateInterestException(normalizedName);
		}
		if (interestSuggestionRepository.existsBySuggestedByIdAndNameIgnoreCaseAndStatus(
				userId,
				normalizedName,
				InterestSuggestionStatus.PENDING
		)) {
			throw new DuplicateInterestSuggestionException(normalizedName);
		}

		User suggestedBy = userService.findById(userId);

		InterestSuggestion suggestion = new InterestSuggestion();
		suggestion.setSuggestedBy(suggestedBy);
		suggestion.setName(normalizedName);
		suggestion.setDescription(request.description());
		suggestion.setStatus(InterestSuggestionStatus.PENDING);

		return interestSuggestionRepository.save(suggestion);
	}

	@Transactional(readOnly = true)
	public List<InterestSuggestion> findByUserId(UUID userId) {
		return interestSuggestionRepository.findBySuggestedByIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public Page<InterestSuggestion> findByStatus(
			InterestSuggestionStatus status,
			Pageable pageable
	) {
		return interestSuggestionRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
	}

	@Transactional
	public void approve(UUID id, UUID adminUserId) {
		InterestSuggestion suggestion = findPendingById(id);
		User admin = userService.findById(adminUserId);

		if (!interestRepository.existsByNameIgnoreCase(suggestion.getName())) {
			Interest newInterest = new Interest();
			newInterest.setName(suggestion.getName());
			newInterest.setDescription(suggestion.getDescription());
			interestRepository.save(newInterest);
		}

		markReviewed(suggestion, InterestSuggestionStatus.APPROVED, admin);
	}

	@Transactional
	public void reject(UUID id, UUID adminUserId) {
		InterestSuggestion suggestion = findPendingById(id);
		User admin = userService.findById(adminUserId);
		markReviewed(suggestion, InterestSuggestionStatus.REJECTED, admin);
	}

	private String normalizeName(String name) {
		return name.trim();
	}

	private InterestSuggestion findPendingById(UUID id) {
		InterestSuggestion suggestion = interestSuggestionRepository.findById(id)
				.orElseThrow(() -> new InterestSuggestionNotFoundException(id));
		if (suggestion.getStatus() != InterestSuggestionStatus.PENDING) {
			throw new InterestSuggestionAlreadyModeratedException(id);
		}
		return suggestion;
	}

	private void markReviewed(
			InterestSuggestion suggestion,
			InterestSuggestionStatus status,
			User admin
	) {
		suggestion.setStatus(status);
		suggestion.setReviewedAt(LocalDateTime.now());
		suggestion.setReviewedBy(admin);
		interestSuggestionRepository.save(suggestion);
	}
}
