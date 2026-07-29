package ru.itmo.nemat.weezzy.recommendation.impression;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfileRecommendationImpressionId implements Serializable {
	private UUID sourceProfileId;
	private UUID targetProfileId;
}
