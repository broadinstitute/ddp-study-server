
package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;
import org.broadinstitute.dsm.util.export.NewOsteoParticipantExporter;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;
import org.broadinstitute.dsm.util.export.DefaultParticipantExporter;

public class ElasticSearchParticipantExporterFactory {

    public static Exportable fromPayload(ParticipantExportPayload payload) {
        return isNewOsteoInstance(payload.getInstanceName())
                ? new NewOsteoParticipantExporter(payload)
                : new DefaultParticipantExporter(payload);
    }

    private static boolean isNewOsteoInstance(String instanceName) {
        return instanceName.equals(StudyPreFilter.NEW_OSTEO_INSTANCE_NAME);
    }

}
