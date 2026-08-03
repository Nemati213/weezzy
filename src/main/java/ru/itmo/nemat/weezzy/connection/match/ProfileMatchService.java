package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.common.dto.CursorPageResponse;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventType;
import ru.itmo.nemat.weezzy.connection.match.dto.ProfileMatchResponse;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteAction;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileMatchService {
	private final ProfileMatchRepository repository;
	private final ProfileService profileService;
	private final ProfileBlockService blockService;
	private final ProfilePairLockService pairLockService;
	private final ProfileVoteRepository voteRepository;
	private final MatchCursorCodec cursorCodec;
	private final ProfileInteractionEventService interactionEventService;

	@Transactional
	public ProfileMatch create(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.equals(secondProfileId)) {
			throw new SelfMatchException(firstProfileId);
		}

		pairLockService.lock(firstProfileId, secondProfileId);
		blockService.ensureInteractionAllowed(firstProfileId, secondProfileId);

		ProfileMatchId matchId = normalizedId(firstProfileId, secondProfileId);

		return repository.findById(matchId)
				.orElseGet(() -> saveNewMatch(
						matchId,
						firstProfileId,
						secondProfileId
				));
	}

	@Transactional(readOnly = true)
	public List<ProfileMatchResponse> findAllMatchesByProfileId(UUID profileId) {
		profileService.findById(profileId);

		List<ProfileMatch> matches =
				repository.findByFirstProfileIdOrSecondProfileIdOrderByCreatedAtDesc(
						profileId,
						profileId
				);

		return toResponses(profileId, matches);
	}

	@Transactional(readOnly = true)
	public CursorPageResponse<ProfileMatchResponse> findMatchesPageByProfileId(
			UUID profileId,
			String encodedCursor,
			int limit
	) {
		profileService.findById(profileId);
		MatchCursor cursor = cursorCodec.decode(encodedCursor);
		PageRequest pageRequest = PageRequest.of(0, limit + 1);

		List<ProfileMatch> fetched = cursor == null
				? repository.findFirstPage(profileId, pageRequest)
				: repository.findNextPage(
						profileId,
						cursor.createdAt(),
						cursor.firstProfileId(),
						cursor.secondProfileId(),
						pageRequest
				);
		boolean hasNext = fetched.size() > limit;
		List<ProfileMatch> page = fetched.stream().limit(limit).toList();
		String nextCursor = hasNext
				? cursorCodec.encode(toCursor(page.getLast()))
				: null;

		return new CursorPageResponse<>(toResponses(profileId, page), nextCursor);
	}

	private List<ProfileMatchResponse> toResponses(
			UUID profileId,
			List<ProfileMatch> matches
	) {
		Set<UUID> matchedProfileIds = matches.stream()
				.map(profileMatch -> otherProfileId(profileId, profileMatch))
				.collect(Collectors.toSet());

		Map<UUID, Profile> profilesById = profileService.findAllByIds(matchedProfileIds)
				.stream()
				.collect(Collectors.toMap(
						Profile::getId,
						Function.identity()
				));

		return matches.stream()
				.map(profileMatch -> ProfileMatchResponse.from(
						profileMatch,
						profilesById.get(otherProfileId(profileId, profileMatch))
				))
				.toList();
	}

	@Transactional(readOnly = true)
	public boolean hasMatch(UUID firstProfileId, UUID secondProfileId) {
		return repository.existsById(normalizedId(firstProfileId, secondProfileId));
	}

	@Transactional
	public void deleteIfExists(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.equals(secondProfileId)) {
			return;
		}

		pairLockService.lock(firstProfileId, secondProfileId);
		ProfileMatchId matchId = normalizedId(firstProfileId, secondProfileId);
		if (repository.existsById(matchId)) {
			repository.deleteById(matchId);
		}
	}

	@Transactional
	public void unmatch(UUID sourceProfileId, UUID matchedProfileId) {
		if (sourceProfileId.equals(matchedProfileId)) {
			throw new SelfMatchException(sourceProfileId);
		}

		pairLockService.lock(sourceProfileId, matchedProfileId);
		ProfileMatchId matchId = normalizedId(sourceProfileId, matchedProfileId);
		if (!repository.existsById(matchId)) {
			throw new MatchNotFoundException(sourceProfileId, matchedProfileId);
		}

		voteRepository.findBySourceProfileIdAndTargetProfileId(
					sourceProfileId,
					matchedProfileId
			)
				.filter(vote -> vote.getAction() == ProfileVoteAction.LIKE)
				.ifPresent(vote -> {
					vote.setAction(ProfileVoteAction.PASS);
					voteRepository.save(vote);
				});
		repository.deleteById(matchId);
		interactionEventService.record(
				sourceProfileId,
				matchedProfileId,
				ProfileInteractionEventType.UNMATCH
		);
	}

	private UUID otherProfileId(UUID sourceProfileId, ProfileMatch profileMatch) {
		if (sourceProfileId.equals(profileMatch.getFirstProfileId())) {
			return profileMatch.getSecondProfileId();
		}
		if (sourceProfileId.equals(profileMatch.getSecondProfileId())) {
			return profileMatch.getFirstProfileId();
		}
		throw new IllegalArgumentException("Profile is not part of match: " + sourceProfileId);
	}

	private ProfileMatch saveNewMatch(
			ProfileMatchId matchId,
			UUID sourceProfileId,
			UUID targetProfileId
	) {
		ProfileMatch profileMatch = new ProfileMatch();
		profileMatch.setFirstProfileId(matchId.getFirstProfileId());
		profileMatch.setSecondProfileId(matchId.getSecondProfileId());

		ProfileMatch savedMatch = repository.saveAndFlush(profileMatch);
		interactionEventService.record(
				sourceProfileId,
				targetProfileId,
				ProfileInteractionEventType.MATCH
		);
		return savedMatch;
	}

	private ProfileMatchId normalizedId(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.toString().compareTo(secondProfileId.toString()) < 0) {
			return new ProfileMatchId(firstProfileId, secondProfileId);
		}

		return new ProfileMatchId(secondProfileId, firstProfileId);
	}

	private MatchCursor toCursor(ProfileMatch profileMatch) {
		return new MatchCursor(
				profileMatch.getCreatedAt(),
				profileMatch.getFirstProfileId(),
				profileMatch.getSecondProfileId()
		);
	}
}
