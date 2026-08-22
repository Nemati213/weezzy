package ru.itmo.nemat.weezzy.lunch.group;

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
public class LunchGroupMemberId implements Serializable {
	private UUID groupId;
	private UUID profileId;
}
