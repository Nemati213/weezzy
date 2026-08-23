package ru.itmo.nemat.weezzy.lunch.chat.cleanup;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LunchChatCleanupService {
	private final LunchChatCleanupRepository repository;
	private final LunchChatCleanupMetrics metrics;
	private final LunchProperties properties;

	@Transactional
	public int deleteExpired(LocalDateTime now, int batchSize) {
		Timer.Sample sample = metrics.start();
		try {
			int deleted = repository.deleteExpiredBatch(
					now.minus(properties.chat().retention()),
					batchSize
			);
			metrics.recordSuccess(sample, deleted);
			return deleted;
		} catch (RuntimeException exception) {
			metrics.recordFailure(sample);
			throw exception;
		}
	}
}
