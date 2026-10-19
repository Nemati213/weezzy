package ru.itmo.nemat.weezzy.team.position.skill;

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
public class TeamPositionSkillId implements Serializable {
	private UUID positionId;
	private UUID skillId;
}
