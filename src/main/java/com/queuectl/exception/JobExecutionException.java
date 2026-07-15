package com.queuectl.exception;

/**
 * Exception thrown when a job fails to execute.
 *
 * <p>Covers scenarios such as:
 * <ul>
 *   <li>Command execution failure (non-zero exit code)</li>
 *   <li>Execution timeout exceeded</li>
 *   <li>Process start failure</li>
 *   <li>Invalid command format</li>
 * </ul>
 */
public class JobExecutionException extends QueueCtlException {

    /** The job ID that failed, if available. */
    private final String jobId;

    public JobExecutionException(String message) {
        super(message);
        this.jobId = null;
    }

    public JobExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.jobId = null;
    }

    public JobExecutionException(String jobId, String message) {
        super(message);
        this.jobId = jobId;
    }

    public JobExecutionException(String jobId, String message, Throwable cause) {
        super(message, cause);
        this.jobId = jobId;
    }

    /**
     * Returns the ID of the job that failed.
     *
     * @return job ID, or null if not associated with a specific job
     */
    public String getJobId() {
        return jobId;
    }
}
