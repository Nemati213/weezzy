package ru.itmo.nemat.weezzy.team.member;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberId implements Serializable {
	private UUID teamId;
	private UUID profileId;
}
