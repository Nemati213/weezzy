package ru.itmo.nemat.weezzy.connection.vote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileVoteService {
	private final ProfileVoteRepository repository;
	private final ProfileService profileService;
	private final ProfileMatchService matchService;
	private final ProfileBlockService blockService;
	private final ProfilePairLockService pairLockService;

	@Transactional
	public ProfileVote vote(UUID sourceProfileId, UUID targetProfileId, ProfileVoteAction action) {
		if (sourceProfileId.equals(targetProfileId)) {
			throw new SelfVoteException(sourceProfileId);
		}

		pairLockService.lock(sourceProfileId, targetProfileId);
		blockService.ensureInteractionAllowed(sourceProfileId, targetProfileId);

		ProfileVote vote = repository.findBySourceProfileIdAndTargetProfileId(
				sourceProfileId,
				targetProfileId
		)
				.orElseGet(() -> {
					ProfileVote profileVote = new ProfileVote();
					profileVote.setSourceProfileId(sourceProfileId);
					profileVote.setTargetProfileId(targetProfileId);
					return profileVote;
				});
		vote.setAction(action);

		ProfileVote savedVote = repository.save(vote);
		if (action == ProfileVoteAction.LIKE) {
			repository.findBySourceProfileIdAndTargetProfileId(targetProfileId, sourceProfileId)
					.filter(reciprocalVote -> reciprocalVote.getAction() == ProfileVoteAction.LIKE)
					.ifPresent(reciprocalVote -> matchService.create(sourceProfileId, targetProfileId));
		} else {
			matchService.deleteIfExists(sourceProfileId, targetProfileId);
		}

		return savedVote;
	}

	@Transactional(readOnly = true)
	public List<ProfileVote> findBySourceProfileId(UUID sourceProfileId) {
		profileService.findById(sourceProfileId);

		return repository.findBySourceProfileId(sourceProfileId);
	}

	@Transactional(readOnly = true)
	public Set<UUID> findVotedTargetProfileIds(UUID sourceProfileId) {
		profileService.findById(sourceProfileId);

		return repository.findBySourceProfileId(sourceProfileId).stream()
				.map(ProfileVote::getTargetProfileId)
				.collect(Collectors.toSet());
	}
}
