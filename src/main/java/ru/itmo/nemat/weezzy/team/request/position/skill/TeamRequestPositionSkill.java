package ru.itmo.nemat.weezzy.team.request.position.skill;

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
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;

@Entity
@Table(name = "team_request_position_skills")
@Getter
@Setter
public class TeamRequestPositionSkill {
	@EmbeddedId
	private TeamRequestPositionSkillId id;

	@MapsId("positionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "position_id", nullable = false)
	private TeamRequestPosition position;

	@MapsId("skillId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "skill_id", nullable = false)
	private Skill skill;
}
