package org.broadinstitute.dsm.model.elastic.export;

import java.util.Objects;

import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;

public class ExportFacadePayload {
    private String index;
    private String docId;
    private GeneratorPayload generatorPayload;

    public ExportFacadePayload(String index, String docId, GeneratorPayload generatorPayload) {
        this.index = Objects.requireNonNull(index);
        this.docId = Objects.requireNonNull(docId);
        this.generatorPayload = Objects.requireNonNull(generatorPayload);
    }

    public String getIndex() {
        return index;
    }

    public String getDocId() {
        return docId;
    }

    public GeneratorPayload getGeneratorPayload() {
        return generatorPayload;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }
}
