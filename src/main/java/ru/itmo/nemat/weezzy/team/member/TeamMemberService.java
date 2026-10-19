package ru.itmo.nemat.weezzy.team.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamNotFoundException;
import ru.itmo.nemat.weezzy.team.TeamProfileNotActiveException;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;
import ru.itmo.nemat.weezzy.team.position.TeamPositionCapacityException;
import ru.itmo.nemat.weezzy.team.position.TeamPositionNotFoundException;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamMemberService {
	private final TeamMemberRepository repository;
	private final TeamAccessService accessService;
	private final ProfileService profileService;
	private final ProfileBlockService blockService;

	@Transactional(propagation = Propagation.MANDATORY)
	public void ensureCanJoin(Team team, Profile candidate, TeamPosition position) {
		accessService.ensureActive(team);
		Profile owner = team.getOwnerProfile();
		if (!isMember(team.getId(), owner.getId())) {
			throw new TeamNotFoundException(team.getId());
		}
		for (Profile profile : java.util.List.of(owner, candidate)) {
			if (profile.getStatus() != ProfileStatus.ACTIVE || profile.getUser() == null) {
				throw new TeamProfileNotActiveException(profile.getId());
			}
			profileService.ensureOwnerAccessAllowed(profile.getId());
		}
		if (!position.getTeam().getId().equals(team.getId())) {
			throw new TeamPositionNotFoundException(position.getId());
		}
		blockService.ensureInteractionAllowed(owner.getId(), candidate.getId());
		ensureNotMember(team.getId(), candidate.getId());
		ensureCapacity(position);
	}

	@Transactional(readOnly = true)
	public boolean isMember(UUID teamId, UUID profileId) {
		return repository.existsById(new TeamMemberId(teamId, profileId));
	}

	@Transactional(readOnly = true)
	public void ensureNotMember(UUID teamId, UUID profileId) {
		if (isMember(teamId, profileId)) {
			throw new TeamMemberAlreadyExistsException();
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void add(Team team, Profile profile, TeamPosition position) {
		if (!position.getTeam().getId().equals(team.getId())) {
			throw new TeamPositionNotFoundException(position.getId());
		}
		ensureNotMember(team.getId(), profile.getId());
		ensureCapacity(position);
		TeamMember member = new TeamMember();
		member.setId(new TeamMemberId(team.getId(), profile.getId()));
		member.setTeam(team);
		member.setProfile(profile);
		member.setPosition(position);
		repository.saveAndFlush(member);
	}

	@Transactional(readOnly = true)
	public void ensureCapacity(TeamPosition position) {
		if (countByPosition(position.getId()) >= position.getCapacity()) {
			throw new TeamPositionCapacityException();
		}
	}

	@Transactional(readOnly = true)
	public long countByTeam(UUID teamId) {
		return repository.countByTeamId(teamId);
	}

	@Transactional(readOnly = true)
	public long countByPosition(UUID positionId) {
		return repository.countByPositionId(positionId);
	}

	@Transactional(readOnly = true)
	public Map<UUID, Long> countByPositions(Collection<UUID> ids) {
		return ids.isEmpty() ? Map.of() : repository.countByPositions(ids).stream()
				.collect(Collectors.toMap(TeamMemberCount::id, TeamMemberCount::count));
	}

	@Transactional(readOnly = true)
	public Map<UUID, Long> countByTeams(Collection<UUID> ids) {
		return ids.isEmpty() ? Map.of() : repository.countByTeams(ids).stream()
				.collect(Collectors.toMap(TeamMemberCount::id, TeamMemberCount::count));
	}
}
