package org.broadinstitute.dsm.export;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.db.DDPInstance;

@Data
public class WorkflowForES {
    DDPInstance instance;
    String ddpParticipantId;
    String workflow;
    String status;
    StudySpecificData studySpecificData;

    private WorkflowForES(DDPInstance instance, String ddpParticipantId, String workflow, String status, StudySpecificData studySpecificData) {
        this.instance = instance;
        this.ddpParticipantId = ddpParticipantId;
        this.workflow = workflow;
        this.status = status;
        this.studySpecificData = studySpecificData;
    }

    public static WorkflowForES createInstance(@NonNull DDPInstance instance, @NonNull String ddpParticipantId,
                                               @NonNull String workflow, @NonNull String status) {
        return new WorkflowForES(instance, ddpParticipantId, workflow, status, null);
    }

    public static WorkflowForES createInstanceWithStudySpecificData(@NonNull DDPInstance instance, @NonNull String ddpParticipantId,
                                                                    @NonNull String workflow, @NonNull String status,
                                                                    @NonNull StudySpecificData studySpecificData) {
        return new WorkflowForES(instance, ddpParticipantId, workflow, status, studySpecificData);
    }

    @Data
    public static class StudySpecificData {
        String subjectId;
        String firstname;
        String lastname;

        public StudySpecificData(String subjectId, String firstname, String lastname) {
            this.subjectId = subjectId;
            this.firstname = firstname;
            this.lastname = lastname;
        }
    }
}
