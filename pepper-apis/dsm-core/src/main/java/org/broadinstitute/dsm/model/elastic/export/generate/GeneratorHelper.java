package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.model.elastic.export.parse.Parser;

public interface GeneratorHelper {
    void setParser(Parser parser);
    void setPayload(GeneratorPayload generatorPayload);
}
