package ru.itmo.nemat.weezzy.outbox.cleanup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OutboxCleanupService {
	private final OutboxEventRepository repository;

	@Transactional
	public int deleteProcessedBefore(LocalDateTime processedBefore, int batchSize) {
		return repository.deleteProcessedBefore(processedBefore, batchSize);
	}
}
