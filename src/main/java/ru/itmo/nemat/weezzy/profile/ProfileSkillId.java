package ru.itmo.nemat.weezzy.profile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSkillId implements Serializable {
	private UUID profileId;
	private UUID skillId;
}
