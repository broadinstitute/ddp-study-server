package org.broadinstitute.ddp.db.dao.copyanswer;

/**
 * An output from copying an answer, which includes
 * the source answer id and the destination id at a minimum.
 */
public class AnswerCopyResult {

    private final long sourceAnswerId;

    private final long destinationAnswerId;

    public AnswerCopyResult(long sourceAnswerId, long destinationAnswerId) {
        this.sourceAnswerId = sourceAnswerId;
        this.destinationAnswerId = destinationAnswerId;
    }

    public long getSourceAnswerId() {
        return sourceAnswerId;
    }

    public long getDestinationAnswerId() {
        return destinationAnswerId;
    }
}
