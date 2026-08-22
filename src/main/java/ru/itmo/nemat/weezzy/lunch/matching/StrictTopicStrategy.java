package ru.itmo.nemat.weezzy.lunch.matching;

import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class StrictTopicStrategy implements MatchingStrategy {
	@Override
	public int priority() {
		return 100;
	}

	@Override
	public MatchingStrategyResult match(
			MatchingBucket bucket,
			LocalDateTime now
	) {
		List<MatchingGroup> groups = new ArrayList<>();
		for (LunchTopic topic : LunchTopic.values()) {
			List<MatchingCandidate> candidatesByTopic = bucket.candidates().stream()
					.filter(candidate -> candidate.topic() == topic)
					.toList();
			groups.addAll(MatchingSupport.maximizeGroups(
					candidatesByTopic,
					topic
			));
		}
		return MatchingSupport.result(bucket.candidates(), groups);
	}
}
