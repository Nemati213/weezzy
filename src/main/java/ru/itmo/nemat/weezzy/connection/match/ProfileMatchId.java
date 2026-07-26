package ru.itmo.nemat.weezzy.connection.match;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchId implements Serializable {
	private UUID firstProfileId;
	private UUID secondProfileId;
}
