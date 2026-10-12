package ru.itmo.nemat.weezzy.team.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.team.Team;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_invitations")
@Getter
@Setter
public class TeamInvitation {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_profile_id", nullable = false)
	private Profile inviterProfile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invitee_profile_id", nullable = false)
	private Profile inviteeProfile;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TeamInvitationStatus status = TeamInvitationStatus.PENDING;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
