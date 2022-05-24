package org.broadinstitute.ddp.studybuilder.task;

public class OsteoDeleteDuplicatedEvents extends DeleteDuplicatedStudyEvents {
    OsteoDeleteDuplicatedEvents() {
        super("CMI-OSTEO", "patches/osteo-to-delete-events.conf");
    }
}
