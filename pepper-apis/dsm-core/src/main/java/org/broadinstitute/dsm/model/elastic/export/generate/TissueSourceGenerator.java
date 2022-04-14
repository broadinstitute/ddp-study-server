package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.model.elastic.export.ExportFacade;
import org.broadinstitute.dsm.model.elastic.export.ExportFacadePayload;

public class TissueSourceGenerator extends ParentChildRelationGenerator {

    public static final String RETURN_DATE = "returnDate";


    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {



        Optional<Map<String, Object>> additionalData = baseCollectionGenerator.getAdditionalData();
        additionalData.ifPresent(map -> super.getAdditionalData().ifPresent(map::putAll));
        return additionalData;
    }

    @Override
    public Map<String, Object> generate() {
        if (RETURN_DATE.equals(getFieldName())) {

            new ExportFacadePayload();
        }
        return super.generate();
    }
}
