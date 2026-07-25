package ru.itmo.nemat.weezzy.profile.goal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileGoalId implements Serializable {
	private UUID profileId;
	private UUID goalId;
}
