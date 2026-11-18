package com.documind.persistence;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    Optional<AnalysisJob> findByIdempotencyKey(String idempotencyKey);

    Optional<AnalysisJob> findByGithubDeliveryId(String githubDeliveryId);

    List<AnalysisJob> findByStatus(AnalysisStatus status);

    List<AnalysisJob> findByRepositoryFullNameAndPullRequestNumber(String repositoryFullName, int pullRequestNumber);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<AnalysisJob> findByStatusAndNextAttemptAtBefore(AnalysisStatus status, Instant now);

    @Query("SELECT j.id FROM AnalysisJob j WHERE (j.status IN :readyStatuses) OR (j.status = :retryStatus AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now)) ORDER BY j.createdAt ASC")
    List<Long> findClaimableJobIds(
            @Param("readyStatuses") Collection<AnalysisStatus> readyStatuses,
            @Param("retryStatus") AnalysisStatus retryStatus,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AnalysisJob j SET j.status = :targetStatus, j.startedAt = COALESCE(j.startedAt, :now), j.updatedAt = :now, j.attemptCount = j.attemptCount + 1 WHERE j.id = :jobId AND ((j.status IN :readyStatuses) OR (j.status = :retryStatus AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now)))")
    int claimJob(
            @Param("jobId") Long jobId,
            @Param("targetStatus") AnalysisStatus targetStatus,
            @Param("readyStatuses") Collection<AnalysisStatus> readyStatuses,
            @Param("retryStatus") AnalysisStatus retryStatus,
            @Param("now") Instant now
    );

    @Query("SELECT j FROM AnalysisJob j WHERE j.status IN :activeStatuses AND j.updatedAt <= :cutoff ORDER BY j.updatedAt ASC")
    List<AnalysisJob> findStuckJobs(
            @Param("activeStatuses") Collection<AnalysisStatus> activeStatuses,
            @Param("cutoff") Instant cutoff
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AnalysisJob j SET j.updatedAt = :updatedAt WHERE j.id = :id")
    int updateLastModified(@Param("id") Long id, @Param("updatedAt") Instant updatedAt);
}
