package org.broadinstitute.dsm.model.elastic.export.generate;

public interface Collector extends HasGeneratorPayload {
    Object collect();
}
