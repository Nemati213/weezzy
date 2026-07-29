package ru.itmo.nemat.weezzy.recommendation.specifications;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVote;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoal;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterest;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkill;
import ru.itmo.nemat.weezzy.recommendation.impression.ProfileRecommendationImpression;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public final class ProfileSpecifications {

	private ProfileSpecifications() {
	}

	public static Specification<Profile> isActive() {
		return (root, query, builder) ->
				builder.equal(root.get("status"), ProfileStatus.ACTIVE);
	}

	public static Specification<Profile> notSelf(UUID sourceProfileId) {
		return (root, query, builder) ->
				builder.notEqual(root.get("id"), sourceProfileId);
	}

	public static Specification<Profile> notVotedBy(UUID sourceProfileId) {
		return (root, query, builder) -> {
			Subquery<UUID> votes = query.subquery(UUID.class);
			Root<ProfileVote> vote = votes.from(ProfileVote.class);
			votes.select(vote.get("targetProfileId"))
					.where(
							builder.equal(vote.get("sourceProfileId"), sourceProfileId),
							builder.equal(vote.get("targetProfileId"), root.get("id"))
					);
			return builder.not(builder.exists(votes));
		};
	}

	public static Specification<Profile> notRecentlyShownTo(
			UUID sourceProfileId,
			LocalDateTime threshold
	) {
		return (root, query, builder) -> {
			Subquery<UUID> impressions = query.subquery(UUID.class);
			Root<ProfileRecommendationImpression> impression =
					impressions.from(ProfileRecommendationImpression.class);
			impressions.select(impression.get("targetProfileId"))
					.where(
							builder.equal(
									impression.get("sourceProfileId"),
									sourceProfileId
							),
							builder.equal(
									impression.get("targetProfileId"),
									root.get("id")
							),
							builder.greaterThan(
									impression.get("shownAt"),
									threshold
							)
					);
			return builder.not(builder.exists(impressions));
		};
	}

	public static Specification<Profile> hasFaculty(String faculty) {
		return (root, query, builder) ->
				faculty == null
						? builder.conjunction()
						: builder.equal(root.get("faculty"), faculty);
	}

	public static Specification<Profile> hasStudyProgram(String studyProgram) {
		return (root, query, builder) ->
				studyProgram == null
						? builder.conjunction()
						: builder.equal(root.get("studyProgram"), studyProgram);
	}

	public static Specification<Profile> inCourses(Set<Integer> courses) {
		return (root, query, builder) ->
				courses.isEmpty()
						? builder.conjunction()
						: root.get("course").in(courses);
	}

	public static Specification<Profile> hasAnySkill(Set<UUID> skillIds) {
		return hasAnyProfileLink(skillIds, ProfileSkill.class, "skillId");
	}

	public static Specification<Profile> hasAnyInterest(Set<UUID> interestIds) {
		return hasAnyProfileLink(interestIds, ProfileInterest.class, "interestId");
	}

	public static Specification<Profile> hasAnyGoal(Set<UUID> goalIds) {
		return hasAnyProfileLink(goalIds, ProfileGoal.class, "goalId");
	}

	private static <T> Specification<Profile> hasAnyProfileLink(
			Set<UUID> targetIds,
			Class<T> linkType,
			String targetIdAttribute
	) {
		return (root, query, builder) -> {
			if (targetIds.isEmpty()) {
				return builder.conjunction();
			}

			Subquery<UUID> links = query.subquery(UUID.class);
			Root<T> link = links.from(linkType);
			links.select(link.get("profileId"))
					.where(
							builder.equal(link.get("profileId"), root.get("id")),
							link.get(targetIdAttribute).in(targetIds)
					);
			return builder.exists(links);
		};
	}
}
