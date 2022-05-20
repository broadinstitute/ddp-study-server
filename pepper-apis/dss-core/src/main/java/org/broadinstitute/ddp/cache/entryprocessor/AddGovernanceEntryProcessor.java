package org.broadinstitute.ddp.cache.entryprocessor;

import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GrantedStudy;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.security.ParticipantAccess;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import java.io.Serializable;

public class AddGovernanceEntryProcessor implements EntryProcessor<Long, DDPAuth, Void>, Serializable {
    @Override
    public Void process(MutableEntry<Long, DDPAuth> entry, Object... arguments) throws EntryProcessorException {
        final DDPAuth value = entry.getValue();

        final Governance governance = (Governance) arguments[0];
        final ParticipantAccess participantAccess = new ParticipantAccess(governance.getGovernedUserGuid());

        StreamEx.of(governance.getGrantedStudies())
                .map(GrantedStudy::getStudyGuid)
                .forEach(participantAccess::addStudyGuid);

        value.addParticipantAccess(participantAccess);

        entry.setValue(value);
        return null;
    }
}
