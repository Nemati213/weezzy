package ru.itmo.nemat.weezzy.lunch.group;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.profile.Profile;

import java.time.LocalDateTime;

@Entity
@Table(name = "lunch_group_members")
@Getter
@Setter
@NoArgsConstructor
public class LunchGroupMember {
	@EmbeddedId
	private LunchGroupMemberId id;

	@MapsId("groupId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private LunchGroup group;

	@MapsId("profileId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lunch_request_id", nullable = false, unique = true)
	private LunchRequest lunchRequest;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	@PrePersist
	void onCreate() {
		joinedAt = LocalDateTime.now();
	}
}
