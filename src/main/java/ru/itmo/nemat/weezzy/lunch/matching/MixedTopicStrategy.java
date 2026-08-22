package ru.itmo.nemat.weezzy.lunch.matching;

import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class MixedTopicStrategy implements MatchingStrategy {
	@Override
	public int priority() {
		return 200;
	}

	@Override
	public MatchingStrategyResult match(
			MatchingBucket bucket,
			LocalDateTime now
	) {
		List<MatchingGroup> groups = MatchingSupport.maximizeGroups(
				bucket.candidates(),
				LunchTopic.CASUAL_CHAT
		);
		return MatchingSupport.result(bucket.candidates(), groups);
	}
}
