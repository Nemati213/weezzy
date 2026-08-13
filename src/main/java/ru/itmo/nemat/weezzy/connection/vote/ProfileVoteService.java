package ru.itmo.nemat.weezzy.connection.vote;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.common.dto.CursorPageResponse;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventType;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.connection.vote.dto.VoteResponse;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.ProfileLikedPayload;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

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
	private final VoteCursorCodec cursorCodec;
	private final ProfileInteractionEventService interactionEventService;
	private final OutboxEventService outboxEventService;

	@Transactional
	public ProfileVote vote(
			UUID sourceProfileId,
			UUID targetProfileId,
			ProfileVoteAction action
	) {
		if (sourceProfileId.equals(targetProfileId)) {
			throw new SelfVoteException(sourceProfileId);
		}

		pairLockService.lock(sourceProfileId, targetProfileId);
		blockService.ensureInteractionAllowed(sourceProfileId, targetProfileId);
		profileService.ensureOwnerAccessAllowed(sourceProfileId);
		profileService.ensureOwnerAccessAllowed(targetProfileId);

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

		ProfileVoteAction previousAction = vote.getAction();
		boolean becameLike = action == ProfileVoteAction.LIKE
				&& previousAction != ProfileVoteAction.LIKE;

		vote.setAction(action);

		ProfileVote savedVote = repository.save(vote);
		interactionEventService.record(
				sourceProfileId,
				targetProfileId,
				action == ProfileVoteAction.LIKE
						? ProfileInteractionEventType.LIKE
						: ProfileInteractionEventType.PASS
		);
		if (action == ProfileVoteAction.LIKE) {
			repository.findBySourceProfileIdAndTargetProfileId(targetProfileId, sourceProfileId)
					.filter(reciprocalVote -> reciprocalVote.getAction() == ProfileVoteAction.LIKE)
					.ifPresentOrElse(
							reciprocalVote -> matchService.create(sourceProfileId, targetProfileId),
							() -> publishLikeIfNeeded(
									sourceProfileId,
									targetProfileId,
									becameLike
							)
					);
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
	public CursorPageResponse<VoteResponse> findPageBySourceProfileId(
			UUID sourceProfileId,
			String encodedCursor,
			int limit
	) {
		profileService.findById(sourceProfileId);
		VoteCursor cursor = cursorCodec.decode(encodedCursor);
		PageRequest pageRequest = PageRequest.of(0, limit + 1);

		List<ProfileVote> fetched = cursor == null
				? repository.findFirstPage(sourceProfileId, pageRequest)
				: repository.findNextPage(
						sourceProfileId,
						cursor.createdAt(),
						cursor.targetProfileId(),
						pageRequest
				);

		boolean hasNext = fetched.size() > limit;
		List<ProfileVote> page = fetched.stream()
				.limit(limit)
				.toList();
		Set<UUID> deletedTargetIds = findDeletedTargetIds(page);
		String nextCursor = hasNext
				? cursorCodec.encode(toCursor(page.getLast()))
				: null;

		return new CursorPageResponse<>(
				page.stream()
						.map(vote -> VoteResponse.from(
								vote,
								deletedTargetIds.contains(vote.getTargetProfileId())
						))
						.toList(),
				nextCursor
		);
	}

	@Transactional(readOnly = true)
	public Set<UUID> findVotedTargetProfileIds(UUID sourceProfileId) {
		profileService.findById(sourceProfileId);

		return repository.findBySourceProfileId(sourceProfileId).stream()
				.map(ProfileVote::getTargetProfileId)
				.collect(Collectors.toSet());
	}

	private VoteCursor toCursor(ProfileVote vote) {
		return new VoteCursor(vote.getCreatedAt(), vote.getTargetProfileId());
	}

	private Set<UUID> findDeletedTargetIds(List<ProfileVote> votes) {
		Set<UUID> targetIds = votes.stream()
				.map(ProfileVote::getTargetProfileId)
				.collect(Collectors.toSet());
		return profileService.findAllByIds(targetIds).stream()
				.filter(profile -> profile.getStatus() == ProfileStatus.DELETED)
				.map(Profile::getId)
				.collect(Collectors.toSet());
	}

	private void publishLikeIfNeeded(
			UUID sourceProfileId,
			UUID targetProfileId,
			boolean becameLike
	) {
		if (!becameLike) {
			return;
		}

		outboxEventService.publish(new ProfileLikedPayload(
				sourceProfileId,
				targetProfileId,
				profileService.findOwnerUserId(targetProfileId)
		));
	}
}
