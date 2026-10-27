package ru.itmo.nemat.weezzy.team.recommendation.impression;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamRecommendationImpressionId implements Serializable {
	private UUID ownerUserId;
	private UUID teamId;
}
