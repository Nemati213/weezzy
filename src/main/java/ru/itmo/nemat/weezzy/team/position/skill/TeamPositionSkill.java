package ru.itmo.nemat.weezzy.team.position.skill;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;

@Entity
@Table(name = "team_position_skills")
@Getter
@Setter
public class TeamPositionSkill {
	@EmbeddedId
	private TeamPositionSkillId id;

	@MapsId("positionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "position_id", nullable = false)
	private TeamPosition position;

	@MapsId("skillId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "skill_id", nullable = false)
	private Skill skill;
}
