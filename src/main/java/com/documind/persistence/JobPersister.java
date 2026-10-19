package com.documind.persistence;

import com.documind.domain.AnalysisJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobPersister {

    private final AnalysisJobRepository repository;

    public JobPersister(AnalysisJobRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes the initial insert in an isolated transaction so that a unique constraint
     * violation (concurrent submission) does not roll back an outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisJob saveNew(AnalysisJob job) {
        return repository.saveAndFlush(job);
    }
}
