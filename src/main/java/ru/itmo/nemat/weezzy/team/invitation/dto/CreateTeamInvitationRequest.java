package ru.itmo.nemat.weezzy.team.invitation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTeamInvitationRequest(@NotNull UUID inviteeProfileId) {
}
