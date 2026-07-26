package ru.itmo.nemat.weezzy.connection.vote.dto;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteAction;

public record CreateVoteRequest(
		@NotNull
		ProfileVoteAction action
) {
}
