
package org.broadinstitute.dsm.util.export;

import org.junit.Assert;
import org.junit.Test;

public class ElasticSearchParticipantExporterFactoryTest {

    @Test
    public void buildNewOsteoExporter() {
        Assert.assertTrue(
                ElasticSearchParticipantExporterFactory.fromPayload(
                        new ParticipantExportPayload(
                                0,
                                null,
                                null,
                                "osteo2",
                                null)) instanceof NewOsteoParticipantExporter
        );
    }

    @Test
    public void buildPlainExporter() {
        Assert.assertTrue(
                ElasticSearchParticipantExporterFactory.fromPayload(
                        new ParticipantExportPayload(
                                0,
                                null,
                                null,
                                "Any study other than osteo2",
                                null)) instanceof DefaultParticipantExporter
        );
    }
}
