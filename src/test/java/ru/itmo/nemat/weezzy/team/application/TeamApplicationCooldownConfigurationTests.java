package ru.itmo.nemat.weezzy.team.application;

import org.junit.jupiter.api.Test;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.application.dto.CreateTeamApplicationRequest;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;
import ru.itmo.nemat.weezzy.team.request.TeamRequest;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamApplicationCooldownConfigurationTests {
	@Test
	void sendingUsesConfiguredCooldownInsteadOfHardcodedDay() {
		var repository = mock(TeamApplicationRepository.class);
		var requests = mock(TeamRequestService.class);
		var profiles = mock(ProfileService.class);
		var publisher = mock(OutboxEventService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
		LocalDateTime now = LocalDateTime.now(clock);
		var service = new TeamApplicationService(repository, requests,
				mock(TeamMemberService.class), profiles, mock(AccountAccessService.class),
				publisher, new TeamApplicationProperties(Duration.ofHours(48)), clock);
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		UUID linkId = UUID.randomUUID();
		UUID requestId = UUID.randomUUID();
		var position = new TeamPosition();
		position.setId(UUID.randomUUID());
		var link = new TeamRequestPosition();
		link.setPosition(position);
		var request = new TeamRequest();
		request.setTeam(new Team());
		link.setRequest(request);
		var profile = new Profile();
		profile.setId(profileId);
		when(profiles.findIdByUserId(userId)).thenReturn(profileId);
		when(profiles.findById(profileId)).thenReturn(profile);
		when(requests.findRequestIdByPosition(linkId)).thenReturn(requestId);
		when(requests.findPosition(linkId)).thenReturn(link);
		when(repository.findLastDeclinedAt(profileId, position.getId()))
				.thenReturn(Optional.of(now.minusHours(30)));

		assertThatThrownBy(() -> service.send(userId, linkId, new CreateTeamApplicationRequest(null)))
				.isInstanceOf(TeamApplicationCooldownException.class)
				.hasMessageContaining(now.plusHours(18).toString());
		verify(repository, never()).saveAndFlush(any());
		verifyNoInteractions(publisher);
	}
}
