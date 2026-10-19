package ru.itmo.nemat.weezzy.team.application.dto;

import jakarta.validation.constraints.Size;

public record CreateTeamApplicationRequest(@Size(max = 2000) String message) {
}
