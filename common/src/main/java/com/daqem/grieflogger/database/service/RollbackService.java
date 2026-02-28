package com.daqem.grieflogger.database.service;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.repository.RollbackRepository;
import com.daqem.grieflogger.model.rollback.RollbackAction;
import com.daqem.grieflogger.model.rollback.RollbackJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing rollback history and undo functionality.
 */
public class RollbackService {

    private final RollbackRepository rollbackRepository;

    public RollbackService(Database database) {
        this.rollbackRepository = new RollbackRepository(database);
    }

    public void createTable() {
        rollbackRepository.createTable();
    }

    public void createIndexes() {
        rollbackRepository.createIndexes();
    }

    /**
     * Record a rollback/restore operation.
     *
     * @param job     The rollback job metadata
     * @param actions The individual block changes
     * @return The job ID, or -1 if failed
     */
    public int recordOperation(RollbackJob job, List<RollbackAction> actions) {
        int jobId = rollbackRepository.insertJob(job);
        if (jobId > 0 && !actions.isEmpty()) {
            rollbackRepository.insertActions(jobId, actions);
        }
        return jobId;
    }

    /**
     * Get the last rollback job performed by a player.
     */
    public Optional<RollbackJob> getLastJob(UUID actorUuid) {
        return rollbackRepository.getLastJobByActor(actorUuid);
    }

    /**
     * Get a rollback job by ID.
     */
    public Optional<RollbackJob> getJobById(int jobId) {
        return rollbackRepository.getJobById(jobId);
    }

    /**
     * Get all actions for a rollback job (for undo).
     */
    public List<RollbackAction> getActionsForJob(int jobId) {
        return rollbackRepository.getActionsByJobId(jobId);
    }

    /**
     * Get recent rollback jobs for a player.
     */
    public List<RollbackJob> getRecentJobs(UUID actorUuid, int limit) {
        return rollbackRepository.getRecentJobsByActor(actorUuid, limit);
    }
}
