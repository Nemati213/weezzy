package ru.itmo.nemat.weezzy.connection.vote;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileVoteId implements Serializable {
	private UUID sourceProfileId;
	private UUID targetProfileId;
}
