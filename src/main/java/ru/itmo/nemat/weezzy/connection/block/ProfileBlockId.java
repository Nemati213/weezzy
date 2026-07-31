package ru.itmo.nemat.weezzy.connection.block;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileBlockId implements Serializable {
	private UUID blockerProfileId;
	private UUID blockedProfileId;
}
