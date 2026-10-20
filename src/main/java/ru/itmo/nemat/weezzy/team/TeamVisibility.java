package ru.itmo.nemat.weezzy.team;

public final class TeamVisibility {
	public static final String CONDITION = """
			team.status = 'ACTIVE'
			AND team.ownerProfile.status = 'ACTIVE'
			AND team.ownerProfile.user IS NOT NULL
			AND EXISTS (
			    SELECT member.id FROM TeamMember member
			    WHERE member.team.id = team.id
			      AND member.profile.id = team.ownerProfile.id
			)
			AND NOT EXISTS (
			    SELECT sanction.id FROM AccountSanction sanction
			    WHERE sanction.targetUserId = team.ownerProfile.user.id
			      AND sanction.status = 'ACTIVE'
			      AND (sanction.type = 'PERMANENT_BAN' OR sanction.expiresAt > :now)
			)
			AND NOT EXISTS (
			    SELECT block.blockerProfileId FROM ProfileBlock block
			    WHERE (block.blockerProfileId = :viewerProfileId
			           AND block.blockedProfileId = team.ownerProfile.id)
			       OR (block.blockerProfileId = team.ownerProfile.id
			           AND block.blockedProfileId = :viewerProfileId)
			)
			""";

	private TeamVisibility() {
	}
}
