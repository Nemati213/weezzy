package ru.itmo.nemat.weezzy.lunch.matching;

import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DesperatePairStrategy implements MatchingStrategy {
	private static final Duration ACTIVATION_WINDOW = Duration.ofMinutes(5);

	@Override
	public int priority() {
		return 300;
	}

	@Override
	public MatchingStrategyResult match(
			MatchingBucket bucket,
			LocalDateTime now
	) {
		if (!now.isAfter(bucket.timeSlot().minus(ACTIVATION_WINDOW))
				|| !now.isBefore(bucket.timeSlot())) {
			return new MatchingStrategyResult(List.of(), bucket.candidates());
		}

		List<MatchingCandidate> ordered = bucket.candidates().stream()
				.sorted(MatchingSupport.CANDIDATE_ORDER)
				.toList();
		List<MatchingGroup> groups = new ArrayList<>();
		for (int index = 0; index + 1 < ordered.size(); index += 2) {
			groups.add(new MatchingGroup(
					LunchTopic.CASUAL_CHAT,
					ordered.subList(index, index + 2)
			));
		}
		return MatchingSupport.result(bucket.candidates(), groups);
	}
}
