package ru.itmo.nemat.weezzy.user.accountdeletion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.interest.suggestion.InterestSuggestionRepository;
import ru.itmo.nemat.weezzy.profile.deletion.ProfileDeletionService;
import ru.itmo.nemat.weezzy.skill.suggestion.SkillSuggestionRepository;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserNotFoundException;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
	private final UserRepository userRepository;
	private final UserService userService;
	private final ProfileDeletionService profileDeletionService;
	private final SkillSuggestionRepository skillSuggestionRepository;
	private final InterestSuggestionRepository interestSuggestionRepository;

	@Transactional
	public void deleteAccount(UUID userId, String currentPassword) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));
		if (!userService.passwordMatches(user, currentPassword)) {
			throw new InvalidCurrentPasswordException();
		}

		profileDeletionService.anonymizeForDeletedUser(userId);
		deleteSuggestionData(userId);
		userRepository.delete(user);
		userRepository.flush();
	}

	private void deleteSuggestionData(UUID userId) {
		skillSuggestionRepository.clearReviewer(userId);
		interestSuggestionRepository.clearReviewer(userId);
		skillSuggestionRepository.deleteAllBySuggestedById(userId);
		interestSuggestionRepository.deleteAllBySuggestedById(userId);
	}
}
