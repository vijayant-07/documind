package com.documind.persistence;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
}
