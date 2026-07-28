package ru.itmo.nemat.weezzy.skill.suggestion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.skill.DuplicateSkillException;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillRepository;
import ru.itmo.nemat.weezzy.skill.suggestion.dto.CreateSkillSuggestionRequest;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillSuggestionService {
	private final SkillSuggestionRepository skillSuggestionRepository;
	private final SkillRepository skillRepository;
	private final UserService userService;

	@Transactional
	public SkillSuggestion create(UUID userId, CreateSkillSuggestionRequest request) {
		String normalizedName = normalizeName(request.name());
		if (skillRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
			throw new DuplicateSkillException(normalizedName);
		}
		if (skillSuggestionRepository.existsBySuggestedByIdAndNameIgnoreCaseAndStatus(
				userId,
				normalizedName,
				SkillSuggestionStatus.PENDING
		)) {
			throw new DuplicateSkillSuggestionException(normalizedName);
		}

		User suggestedBy = userService.findById(userId);

		SkillSuggestion suggestion = new SkillSuggestion();
		suggestion.setSuggestedBy(suggestedBy);
		suggestion.setName(normalizedName);
		suggestion.setDescription(request.description());
		suggestion.setStatus(SkillSuggestionStatus.PENDING);

		return skillSuggestionRepository.save(suggestion);
	}

	@Transactional(readOnly = true)
	public List<SkillSuggestion> findByUserId(UUID userId) {
		return skillSuggestionRepository.findBySuggestedByIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public Page<SkillSuggestion> findByStatus(
			SkillSuggestionStatus status,
			Pageable pageable
	) {
		return skillSuggestionRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
	}

	@Transactional
	public void approve(UUID id, UUID adminUserId) {
		SkillSuggestion suggestion = findPendingById(id);
		User admin = userService.findById(adminUserId);

		if (!skillRepository.existsByNameIgnoreCase(suggestion.getName())) {
			Skill newSkill = new Skill();
			newSkill.setName(suggestion.getName());
			newSkill.setDescription(suggestion.getDescription());
			skillRepository.save(newSkill);
		}

		markReviewed(suggestion, SkillSuggestionStatus.APPROVED, admin);
	}

	@Transactional
	public void reject(UUID id, UUID adminUserId) {
		SkillSuggestion suggestion = findPendingById(id);
		User admin = userService.findById(adminUserId);
		markReviewed(suggestion, SkillSuggestionStatus.REJECTED, admin);
	}

	private String normalizeName(String name) {
		return name.trim();
	}

	private SkillSuggestion findPendingById(UUID id) {
		SkillSuggestion suggestion = skillSuggestionRepository.findById(id)
				.orElseThrow(() -> new SkillSuggestionNotFoundException(id));
		if (suggestion.getStatus() != SkillSuggestionStatus.PENDING) {
			throw new SkillSuggestionAlreadyModeratedException(id);
		}
		return suggestion;
	}

	private void markReviewed(
			SkillSuggestion suggestion,
			SkillSuggestionStatus status,
			User admin
	) {
		suggestion.setStatus(status);
		suggestion.setReviewedAt(LocalDateTime.now());
		suggestion.setReviewedBy(admin);
		skillSuggestionRepository.save(suggestion);
	}
}
