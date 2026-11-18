package com.documind.worker;

import com.documind.domain.AnalysisStatus;
import com.documind.persistence.AnalysisJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Executes atomic job claim updates in an independent transaction.
 * Ensures duplicate or concurrent workers cannot claim the same job.
 */
@Service
public class JobClaimService {

    private final AnalysisJobRepository repository;

    public JobClaimService(AnalysisJobRepository repository) {
        this.repository = repository;
    }

    /**
     * Atomically claims a job for processing.
     * Transitions status from RECEIVED/QUEUED/RETRYABLE_FAILURE to PROCESSING,
     * increments attempt count, and sets started_at and updated_at.
     *
     * @param jobId The job ID to claim
     * @param now   Current timestamp
     * @return true if the job was successfully claimed by this call; false if already claimed or unavailable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimJob(Long jobId, Instant now) {
        int updated = repository.claimJob(
                jobId,
                AnalysisStatus.PROCESSING,
                List.of(AnalysisStatus.RECEIVED, AnalysisStatus.QUEUED),
                AnalysisStatus.RETRYABLE_FAILURE,
                now
        );
        return updated > 0;
    }
}

