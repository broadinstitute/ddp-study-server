package org.broadinstitute.dsm.service.medicalrecord;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;

/**
 * Provides various operations on DDP instances for a given context.
 * This encapsulates a DDPInstance interface across a few different concepts/classes, and provides a ways to
 * implement an version for testing.
 */
public interface DDPInstanceProvider {
    /**
     * Get all DDP instances that are applicable for the current context.
     */
    List<DDPInstance> getApplicableInstances();

    /**
     * Convert a DDPInstance to a DDPInstanceDto, and perhaps return a different instance based on current instance
     * and participant.
     */
    DDPInstance getEffectiveInstance(DDPInstance ddpInstance, String ddpParticipantId);

    /**
     * Get the sequence number for the last participant institution request stored for the given instance.
     * Returns empty if no sequence number is found.
     */
    Optional<Long> getInstanceSequenceNumber(DDPInstance ddpInstance);

    Long getInstanceSequenceNumber(DDPInstance ddpInstance, Connection conn);

    void updateInstanceSequenceNumber(DDPInstance ddpInstance, long sequenceNumber, Connection conn);
}
