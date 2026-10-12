package ru.itmo.nemat.weezzy.team.member;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.team.Team;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_members")
@Getter
@Setter
public class TeamMember {
	@EmbeddedId
	private TeamMemberId id;

	@MapsId("teamId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@MapsId("profileId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	@PrePersist
	void onCreate() {
		joinedAt = LocalDateTime.now();
	}
}
