package ru.itmo.nemat.weezzy.connection.vote.dto;

import ru.itmo.nemat.weezzy.connection.vote.ProfileVote;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteAction;

import java.time.LocalDateTime;
import java.util.UUID;

public record VoteResponse(
		UUID sourceProfileId,
		UUID targetProfileId,
		ProfileVoteAction action,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static VoteResponse from(ProfileVote profileVote) {
		return new VoteResponse(
				profileVote.getSourceProfileId(),
				profileVote.getTargetProfileId(),
				profileVote.getAction(),
				profileVote.getCreatedAt(),
				profileVote.getUpdatedAt()
		);
	}
}
