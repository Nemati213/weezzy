package ru.itmo.nemat.weezzy.team.recommendation.page;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationPageResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationResponse;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSession;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionNotFoundException;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static ru.itmo.nemat.weezzy.support.ProfilePhotoTestData.insertReadyPhoto;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRecommendationPageIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private TeamRecommendationPageService pages;
	@Autowired private TeamRecommendationCursorCodec cursors;
	@Autowired private TeamRecommendationSessionRepository sessions;
	@Autowired private TeamRecommendationEntryRepository entries;
	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@MockitoBean private Clock clock;
	private UUID viewer;
	private UUID viewerProfile;
	private UUID ownerProfile;
	private LocalDateTime now;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() throws Exception {
		now = LocalDateTime.now().withNano(0);
		setTime(now);
		TestProfile profile = profile();
		viewer = UUID.fromString(profile.owner().userId());
		viewerProfile = UUID.fromString(profile.id());
		ownerProfile = UUID.fromString(profile().id());
	}

	@Test
	void forwardBackwardAndDirectRestoreKeepEntryIdsAndDeliveryTime() {
		var teams = List.of(team(), team(), team(), team(), team());
		UUID session = session(2, teams);
		var first = pages.findForUser(viewer, session, null);
		assertTeams(first, teams.subList(0, 2));
		assertThat(first.previousCursor()).isNull();
		assertThat(entries.findWindow(session, 2, 5))
				.allSatisfy(entry -> assertThat(entry.getFirstDeliveredAt()).isNull());
		setTime(now.plusMinutes(1));
		var second = pages.findForUser(viewer, session, first.nextCursor());
		assertTeams(second, teams.subList(2, 4));
		var last = pages.findForUser(viewer, session, second.nextCursor());
		assertTeams(last, teams.subList(4, 5));
		assertThat(last.nextCursor()).isNull();
		assertThat(pages.findForUser(viewer, session, last.previousCursor())).isEqualTo(second);
		assertThat(pages.findForUser(viewer, session, second.previousCursor())).isEqualTo(first);
		assertThat(pages.findForUser(viewer, session, first.pageCursor())).isEqualTo(first);
		assertThat(entries.findWindow(session, 0, 2)).allSatisfy(entry -> {
			assertThat(entry.getFirstDeliveredAt()).isEqualTo(now);
			assertThat(entry.getFirstShownAt()).isNull();
		});
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_recommendation_impressions WHERE owner_user_id = ?
				""", Long.class, viewer)).isZero();
	}

	@Test
	void emptyWindowDoesNotShiftFollowingPageAndCanBecomeAvailableAgain() {
		var teams = List.of(team(), team(), team(), team());
		UUID session = session(2, teams);
		jdbc.update("UPDATE team_requests SET status = 'CLOSED' WHERE id IN (?, ?)",
				teams.get(0).request(), teams.get(1).request());
		var first = pages.findForUser(viewer, session, null);
		assertThat(first.items()).isEmpty();
		assertThat(first.nextCursor()).isNotNull();
		var second = pages.findForUser(viewer, session, first.nextCursor());
		assertTeams(second, teams.subList(2, 4));
		assertThat(entries.findWindow(session, 0, 2))
				.allSatisfy(entry -> assertThat(entry.getFirstDeliveredAt()).isNull());
		jdbc.update("UPDATE team_requests SET status = 'OPEN' WHERE id = ?", teams.getFirst().request());
		var restored = pages.findForUser(viewer, session, second.previousCursor());
		assertTeams(restored, teams.subList(0, 1));
		assertThat(restored.items().getFirst().entryId())
				.isEqualTo(entries.findWindow(session, 0, 1).getFirst().getId());
	}

	@Test
	void deletedTeamRetainsItsSlotAndAnEmptySessionRemainsReadable() {
		var first = team();
		var second = team();
		UUID session = session(1, List.of(first, second));
		jdbc.update("DELETE FROM teams WHERE id = ?", first.team());
		var page = pages.findForUser(viewer, session, null);
		assertThat(page.items()).isEmpty();
		assertThat(entries.findWindow(session, 0, 1).getFirst().getTeamId()).isNull();
		assertTeams(pages.findForUser(viewer, session, page.nextCursor()), List.of(second));
		UUID emptySession = session(20, List.of());
		var empty = pages.findForUser(viewer, emptySession, null);
		assertThat(empty.items()).isEmpty();
		assertThat(empty.nextCursor()).isNull();
		assertThat(empty.previousCursor()).isNull();
		assertThat(pages.findForUser(viewer, emptySession, empty.pageCursor())).isEqualTo(empty);
	}

	@Test
	void rereadRechecksPendingApplicationsAndUsesAnotherAvailablePosition() {
		var team = team();
		UUID session = session(20, List.of(team));
		var original = pages.findForUser(viewer, session, null).items().getFirst();
		jdbc.update("""
				INSERT INTO team_applications (id, position_id, applicant_profile_id, status)
				VALUES (?, ?, ?, 'PENDING')
				""", UUID.randomUUID(), team.link(), viewerProfile);
		assertThat(pages.findForUser(viewer, session, null).items()).isEmpty();
		UUID position = UUID.randomUUID();
		jdbc.update("INSERT INTO team_positions (id, team_id, name, capacity) VALUES (?, ?, 'Design', 1)",
				position, team.team());
		jdbc.update("INSERT INTO team_request_positions (id, request_id, team_position_id) VALUES (?, ?, ?)",
				UUID.randomUUID(), team.request(), position);
		var updated = pages.findForUser(viewer, session, null).items().getFirst();
		assertThat(updated.entryId()).isEqualTo(original.entryId());
		assertThat(updated.recommendedTeamPositionId()).isEqualTo(position);
		assertThat(updated.options()).hasSize(1);
	}

	@Test
	void foreignMissingAndExpiredSessionsStayHiddenEvenWithInvalidCursor() throws Exception {
		UUID session = session(20, List.of(team()));
		UUID other = UUID.fromString(AuthenticatedTestUser.register(mockMvc, objectMapper).userId());
		assertThatThrownBy(() -> pages.findForUser(other, session, "invalid"))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		assertThatThrownBy(() -> pages.findForUser(viewer, UUID.randomUUID(), "invalid"))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		assertThatThrownBy(() -> pages.findForUser(viewer, session, cursors.encode(UUID.randomUUID(), 0)))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
		setTime(now.plusDays(1));
		assertThatThrownBy(() -> pages.findForUser(viewer, session, "invalid"))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		assertThat(entries.findWindow(session, 0, 1).getFirst().getFirstDeliveredAt()).isNull();
	}

	@Test
	void sanctionsAreRecheckedEvenForEmptyPages() {
		UUID session = session(20, List.of());
		pages.findForUser(viewer, session, null);
		jdbc.update("""
				INSERT INTO account_sanctions
				(id, target_user_id, target_profile_id, type, status, reason,
				 created_by_user_id, created_at, updated_at)
				VALUES (?, ?, ?, 'PERMANENT_BAN', 'ACTIVE', 'Test', ?, ?, ?)
				""", UUID.randomUUID(), viewer, viewerProfile, viewer, now, now);
		assertThatThrownBy(() -> pages.findForUser(viewer, session, null))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void queryCountStaysConstantFromOneToTwentyTeamsWithNestedMembersAndSkills() throws Exception {
		UUID skill = UUID.randomUUID();
		jdbc.update("INSERT INTO skills (id, name, created_at) VALUES (?, ?, ?)", skill, "Skill " + skill, now);
		jdbc.update("INSERT INTO profile_skills (profile_id, skill_id, created_at) VALUES (?, ?, ?)",
				viewerProfile, skill, now);
		List<Seed> teams = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ownerProfile = UUID.fromString(profile().id());
			insertReadyPhoto(jdbc, ownerProfile);
			var team = team();
			jdbc.update("INSERT INTO team_position_skills (position_id, skill_id) VALUES (?, ?)",
					team.position(), skill);
			teams.add(team);
		}
		UUID single = session(1, teams.subList(0, 1));
		UUID full = session(20, teams);
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			statistics.clear();
			assertTeams(pages.findForUser(viewer, single, null), teams.subList(0, 1));
			long count = statistics.getPrepareStatementCount();
			statistics.clear();
			var page = pages.findForUser(viewer, full, null);
			assertTeams(page, teams);
			assertThat(statistics.getPrepareStatementCount()).isEqualTo(count);
			assertThat(count).isLessThanOrEqualTo(20);
			assertThat(page.items()).allSatisfy(item -> {
				assertThat(item.team().members().getFirst().photos()).hasSize(1);
				assertThat(item.options().getFirst().matchedSkillIds()).containsExactly(skill);
			});
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	private UUID session(int pageSize, List<Seed> teams) {
		var session = new TeamRecommendationSession();
		session.setOwnerUserId(viewer);
		session.setClientRequestId(UUID.randomUUID());
		session.setCreatedAt(now);
		session.setExpiresAt(now.plusDays(1));
		session.setPageSize(pageSize);
		session.setEntryCount(teams.size());
		UUID id = sessions.saveAndFlush(session).getId();
		for (int i = 0; i < teams.size(); i++) {
			var entry = new TeamRecommendationEntry();
			entry.setSessionId(id);
			entry.setOrdinal(i);
			entry.setTeamId(teams.get(i).team());
			entries.saveAndFlush(entry);
		}
		return id;
	}

	private Seed team() {
		UUID team = UUID.randomUUID();
		UUID founder = UUID.randomUUID();
		UUID position = UUID.randomUUID();
		UUID request = UUID.randomUUID();
		UUID link = UUID.randomUUID();
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name, purpose) VALUES (?, ?, 'Team', 'Project')",
				team, ownerProfile);
		jdbc.update("""
				INSERT INTO team_positions (id, team_id, name, capacity)
				VALUES (?, ?, 'Founder', 1), (?, ?, 'Backend', 2)
				""", founder, team, position, team);
		jdbc.update("INSERT INTO team_members (team_id, profile_id, position_id) VALUES (?, ?, ?)",
				team, ownerProfile, founder);
		jdbc.update("""
				INSERT INTO team_requests (id, team_id, title, recruitment_deadline)
				VALUES (?, ?, 'Recruitment', ?)
				""", request, team, now.plusDays(2));
		jdbc.update("INSERT INTO team_request_positions (id, request_id, team_position_id) VALUES (?, ?, ?)",
				link, request, position);
		return new Seed(team, position, request, link);
	}

	private TestProfile profile() throws Exception {
		var result = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("User");
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", UUID.fromString(result.id()));
		return result;
	}

	private void setTime(LocalDateTime value) {
		ZoneId zone = ZoneId.systemDefault();
		when(clock.getZone()).thenReturn(zone);
		when(clock.instant()).thenReturn(value.atZone(zone).toInstant());
	}

	private void assertTeams(TeamRecommendationPageResponse page, List<Seed> expected) {
		assertThat(page.items()).extracting(item -> item.team().id())
				.containsExactlyElementsOf(expected.stream().map(Seed::team).toList());
		assertThat(page.items()).extracting(TeamRecommendationResponse::entryId)
				.doesNotHaveDuplicates().doesNotContainNull();
	}

	private record Seed(UUID team, UUID position, UUID request, UUID link) {
	}
}
