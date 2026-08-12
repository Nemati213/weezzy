package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.itmo.nemat.weezzy.recommendation.dto.RecommendationFilter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecommendationRankingRepository {
	private static final String CURSOR_MARKER = "/* CURSOR_CONDITION */";
	private static final String CURSOR_CONDITION = """
			AND (
				score < :cursorScore
				OR (score = :cursorScore AND profile_id > :cursorProfileId)
			)
			""";
	private static final String RANKING_SQL = """
			WITH candidate_profiles AS (
				SELECT profile.id
				FROM profiles profile
				WHERE profile.status = 'ACTIVE'
					AND profile.id <> :sourceProfileId
					AND NOT EXISTS (
						SELECT 1
						FROM account_sanctions sanction
						WHERE sanction.target_user_id = profile.user_id
							AND sanction.status = 'ACTIVE'
							AND (
								sanction.type = 'PERMANENT_BAN'
								OR sanction.expires_at > CURRENT_TIMESTAMP
							)
					)
					AND (
						:hasFacultyFilter = FALSE
						OR profile.faculty = :faculty
					)
					AND (
						:hasStudyProgramFilter = FALSE
						OR profile.study_program = :studyProgram
					)
					AND (
						:hasCoursesFilter = FALSE
						OR profile.course IN (:courses)
					)
					AND (
						:hasSkillFilter = FALSE
						OR EXISTS (
							SELECT 1
							FROM profile_skills filtered_skill
							WHERE filtered_skill.profile_id = profile.id
								AND filtered_skill.skill_id IN (:skillIds)
						)
					)
					AND (
						:hasInterestFilter = FALSE
						OR EXISTS (
							SELECT 1
							FROM profile_interests filtered_interest
							WHERE filtered_interest.profile_id = profile.id
								AND filtered_interest.interest_id IN (:interestIds)
						)
					)
					AND (
						:hasGoalFilter = FALSE
						OR EXISTS (
							SELECT 1
							FROM profile_goals filtered_goal
							WHERE filtered_goal.profile_id = profile.id
								AND filtered_goal.goal_id IN (:goalIds)
						)
					)
					AND NOT EXISTS (
						SELECT 1
						FROM profile_votes vote
						WHERE vote.source_profile_id = :sourceProfileId
							AND vote.target_profile_id = profile.id
					)
					AND NOT EXISTS (
						SELECT 1
						FROM profile_blocks block
						WHERE (
							block.blocker_profile_id = :sourceProfileId
							AND block.blocked_profile_id = profile.id
						) OR (
							block.blocker_profile_id = profile.id
							AND block.blocked_profile_id = :sourceProfileId
						)
					)
					AND NOT EXISTS (
						SELECT 1
						FROM profile_recommendation_impressions impression
						WHERE impression.source_profile_id = :sourceProfileId
							AND impression.target_profile_id = profile.id
							AND impression.shown_at >= :cooldownThreshold
					)
			),
			skill_matches AS (
				SELECT candidate_skill.profile_id,
					COUNT(*) AS matched_skill_count
				FROM profile_skills candidate_skill
				JOIN profile_skills source_skill
					ON source_skill.skill_id = candidate_skill.skill_id
					AND source_skill.profile_id = :sourceProfileId
				JOIN candidate_profiles candidate
					ON candidate.id = candidate_skill.profile_id
				GROUP BY candidate_skill.profile_id
			),
			interest_matches AS (
				SELECT candidate_interest.profile_id,
					COUNT(*) AS matched_interest_count
				FROM profile_interests candidate_interest
				JOIN profile_interests source_interest
					ON source_interest.interest_id = candidate_interest.interest_id
					AND source_interest.profile_id = :sourceProfileId
				JOIN candidate_profiles candidate
					ON candidate.id = candidate_interest.profile_id
				GROUP BY candidate_interest.profile_id
			),
			goal_matches AS (
				SELECT candidate_goal.profile_id,
					COUNT(*) AS matched_goal_count
				FROM profile_goals candidate_goal
				JOIN profile_goals source_goal
					ON source_goal.goal_id = candidate_goal.goal_id
					AND source_goal.profile_id = :sourceProfileId
				JOIN candidate_profiles candidate
					ON candidate.id = candidate_goal.profile_id
				GROUP BY candidate_goal.profile_id
			),
			scored_candidates AS (
				SELECT candidate.id AS profile_id,
					COALESCE(skill.matched_skill_count, 0) AS matched_skill_count,
					COALESCE(interest.matched_interest_count, 0) AS matched_interest_count,
					COALESCE(goal.matched_goal_count, 0) AS matched_goal_count,
					COALESCE(skill.matched_skill_count, 0) * :skillWeight
						+ COALESCE(interest.matched_interest_count, 0) * :interestWeight
						+ COALESCE(goal.matched_goal_count, 0) * :goalWeight AS score
				FROM candidate_profiles candidate
				LEFT JOIN skill_matches skill ON skill.profile_id = candidate.id
				LEFT JOIN interest_matches interest ON interest.profile_id = candidate.id
				LEFT JOIN goal_matches goal ON goal.profile_id = candidate.id
			)
			SELECT profile_id,
				score,
				matched_skill_count,
				matched_interest_count,
				matched_goal_count
			FROM scored_candidates
			WHERE score > 0
				/* CURSOR_CONDITION */
			ORDER BY score DESC, profile_id ASC
			LIMIT :queryLimit
			""";

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final RecommendationProperties properties;

	public List<RankedProfileProjection> findRankedProfiles(
			UUID sourceProfileId,
			RecommendationCursor cursor,
			int queryLimit,
			RecommendationFilter filter,
			LocalDateTime cooldownThreshold
	) {
		RecommendationFilter effectiveFilter = filter == null
				? emptyFilter()
				: filter;
		String sql = RANKING_SQL.replace(
				CURSOR_MARKER,
				cursor == null ? "" : CURSOR_CONDITION
		);
		MapSqlParameterSource params = parameters(
				sourceProfileId,
				cursor,
				queryLimit,
				effectiveFilter,
				cooldownThreshold
		);

		return jdbcTemplate.query(
				sql,
				params,
				DataClassRowMapper.newInstance(RankedProfileProjection.class)
		);
	}

	private MapSqlParameterSource parameters(
			UUID sourceProfileId,
			RecommendationCursor cursor,
			int queryLimit,
			RecommendationFilter filter,
			LocalDateTime cooldownThreshold
	) {
		RecommendationProperties.Weights weights = properties.weights();
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("sourceProfileId", sourceProfileId)
				.addValue("skillWeight", weights.skill())
				.addValue("interestWeight", weights.interest())
				.addValue("goalWeight", weights.goal())
				.addValue("queryLimit", queryLimit)
				.addValue("cooldownThreshold", cooldownThreshold)
				.addValue("hasFacultyFilter", filter.faculty() != null)
				.addValue("faculty", filter.faculty() == null ? "" : filter.faculty())
				.addValue("hasStudyProgramFilter", filter.studyProgram() != null)
				.addValue(
						"studyProgram",
						filter.studyProgram() == null ? "" : filter.studyProgram()
				)
				.addValue("hasCoursesFilter", !filter.courses().isEmpty())
				.addValue("courses", valuesOrSentinel(filter.courses(), -1))
				.addValue("hasSkillFilter", !filter.skillIds().isEmpty())
				.addValue("skillIds", valuesOrSentinel(
						filter.skillIds(),
						new UUID(0, 0)
				))
				.addValue("hasInterestFilter", !filter.interestIds().isEmpty())
				.addValue("interestIds", valuesOrSentinel(
						filter.interestIds(),
						new UUID(0, 0)
				))
				.addValue("hasGoalFilter", !filter.goalIds().isEmpty())
				.addValue("goalIds", valuesOrSentinel(
						filter.goalIds(),
						new UUID(0, 0)
				));

		if (cursor != null) {
			params.addValue("cursorScore", cursor.score())
					.addValue("cursorProfileId", cursor.profileId());
		}

		return params;
	}

	private <T> Collection<T> valuesOrSentinel(Collection<T> values, T sentinel) {
		if (!values.isEmpty()) {
			return values;
		}
		return List.of(sentinel);
	}

	private RecommendationFilter emptyFilter() {
		return new RecommendationFilter(null, null, null, null, null, null);
	}
}
