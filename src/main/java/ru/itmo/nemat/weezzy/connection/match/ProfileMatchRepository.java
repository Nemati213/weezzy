package ru.itmo.nemat.weezzy.connection.match;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProfileMatchRepository extends JpaRepository<ProfileMatch, ProfileMatchId> {
	@Query(value = """
			SELECT profile_match.*
			FROM profile_matches profile_match
			JOIN profiles matched_profile
				ON matched_profile.id = CASE
					WHEN profile_match.first_profile_id = :profileId
						THEN profile_match.second_profile_id
					ELSE profile_match.first_profile_id
				END
			WHERE (
					profile_match.first_profile_id = :profileId
					OR profile_match.second_profile_id = :profileId
				)
				AND NOT EXISTS (
					SELECT 1
					FROM account_sanctions sanction
					WHERE sanction.target_user_id = matched_profile.user_id
						AND sanction.status = 'ACTIVE'
						AND (
							sanction.type = 'PERMANENT_BAN'
							OR sanction.expires_at > :now
						)
				)
			ORDER BY profile_match.created_at DESC
			""", nativeQuery = true)
	List<ProfileMatch> findVisibleByProfileId(
			@Param("profileId") UUID profileId,
			@Param("now") LocalDateTime now
	);

	@Query(value = """
			SELECT profile_match.*
			FROM profile_matches profile_match
			JOIN profiles matched_profile
				ON matched_profile.id = CASE
					WHEN profile_match.first_profile_id = :profileId
						THEN profile_match.second_profile_id
					ELSE profile_match.first_profile_id
				END
			WHERE (
					profile_match.first_profile_id = :profileId
					OR profile_match.second_profile_id = :profileId
				)
				AND NOT EXISTS (
					SELECT 1
					FROM account_sanctions sanction
					WHERE sanction.target_user_id = matched_profile.user_id
						AND sanction.status = 'ACTIVE'
						AND (
							sanction.type = 'PERMANENT_BAN'
							OR sanction.expires_at > :now
						)
				)
			ORDER BY profile_match.created_at DESC,
				profile_match.first_profile_id DESC,
				profile_match.second_profile_id DESC
			""", nativeQuery = true)
	List<ProfileMatch> findFirstPage(
			@Param("profileId") UUID profileId,
			@Param("now") LocalDateTime now,
			Pageable pageable
	);

	@Query(value = """
			SELECT profile_match.*
			FROM profile_matches profile_match
			JOIN profiles matched_profile
				ON matched_profile.id = CASE
					WHEN profile_match.first_profile_id = :profileId
						THEN profile_match.second_profile_id
					ELSE profile_match.first_profile_id
				END
			WHERE (profile_match.first_profile_id = :profileId
					OR profile_match.second_profile_id = :profileId)
				AND NOT EXISTS (
					SELECT 1
					FROM account_sanctions sanction
					WHERE sanction.target_user_id = matched_profile.user_id
						AND sanction.status = 'ACTIVE'
						AND (
							sanction.type = 'PERMANENT_BAN'
							OR sanction.expires_at > :now
						)
				)
				AND (
					profile_match.created_at < :createdAt
					OR (
						profile_match.created_at = :createdAt
						AND (
							profile_match.first_profile_id < :firstProfileId
							OR (
								profile_match.first_profile_id = :firstProfileId
								AND profile_match.second_profile_id < :secondProfileId
							)
						)
					)
				)
			ORDER BY profile_match.created_at DESC,
				profile_match.first_profile_id DESC,
				profile_match.second_profile_id DESC
			""", nativeQuery = true)
	List<ProfileMatch> findNextPage(
			@Param("profileId") UUID profileId,
			@Param("now") LocalDateTime now,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("firstProfileId") UUID firstProfileId,
			@Param("secondProfileId") UUID secondProfileId,
			Pageable pageable
	);
}
