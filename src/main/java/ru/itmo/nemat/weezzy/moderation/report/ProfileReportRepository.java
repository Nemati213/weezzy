package ru.itmo.nemat.weezzy.moderation.report;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProfileReportRepository extends JpaRepository<ProfileReport, UUID> {
	boolean existsByReporterProfileIdAndTargetProfileIdAndStatusIn(
			UUID reporterProfileId,
			UUID targetProfileId,
			Collection<ProfileReportStatus> statuses
	);

	Page<ProfileReport> findByStatusOrderByCreatedAtAscIdAsc(
			ProfileReportStatus status,
			Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT profileReport
			FROM ProfileReport profileReport
			WHERE profileReport.id = :reportId
			""")
	Optional<ProfileReport> findByIdForUpdate(@Param("reportId") UUID reportId);
}
