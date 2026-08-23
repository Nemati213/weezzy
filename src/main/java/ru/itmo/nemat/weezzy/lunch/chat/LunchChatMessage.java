package ru.itmo.nemat.weezzy.lunch.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;
import ru.itmo.nemat.weezzy.profile.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lunch_group_messages")
@Getter
@Setter
@NoArgsConstructor
public class LunchChatMessage {
	public static final int MAX_CONTENT_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private LunchGroup group;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_profile_id", nullable = false, updatable = false)
	private Profile senderProfile;

	@Column(name = "client_message_id", nullable = false, updatable = false)
	private UUID clientMessageId;

	@Column(nullable = false, updatable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
