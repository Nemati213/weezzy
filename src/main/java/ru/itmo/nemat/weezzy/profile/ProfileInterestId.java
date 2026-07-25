package ru.itmo.nemat.weezzy.profile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileInterestId implements Serializable {
	private UUID profileId;
	private UUID interestId;
}
