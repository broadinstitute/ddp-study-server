package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.patch.Patch;

public class TissueSourceGenerator extends ParentChildRelationGenerator {

    public static final String RETURN_DATE = "returnDate";
    DDPInstanceDao ddpInstanceDao;


    public TissueSourceGenerator() {
        ddpInstanceDao = new DDPInstanceDao();
    }

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        if (RETURN_DATE.equals(getFieldName())) {
            DDPInstanceDto ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(generatorPayload.getInstanceName()).orElseThrow();
            OncHistoryDetail oncHistoryDetail = new OncHistoryDetail();
            String oncHistoryDetailId = generatorPayload.getParentId();
            oncHistoryDetail.setOncHistoryDetailId(Integer.valueOf(oncHistoryDetailId));
            if (StringUtils.isNotBlank(generatorPayload.getValue().toString())) {
                oncHistoryDetail.setRequest(OncHistoryDetail.STATUS_RETURNED);
            } else {
                Patch patch = new Patch();
                patch.setId(generatorPayload.getParentId());
                GeneratorPayload oncHistoryDetailGeneratorPayload = new GeneratorPayload(generatorPayload.getNameValue(), patch);
                UnableObtainTissueStrategy unableObtainTissueStrategy = new UnableObtainTissueStrategy(oncHistoryDetailGeneratorPayload);
                oncHistoryDetail.setRequest(String.valueOf(unableObtainTissueStrategy.generate().get(OncHistoryDetail.STATUS_REQUEST)));
            }
            UpsertPainlessFacade.of(getTableAlias(), oncHistoryDetail, ddpInstanceDto, OncHistoryDetail.ONC_HISTORY_DETAIL_ID,
                    OncHistoryDetail.ONC_HISTORY_DETAIL_ID, oncHistoryDetailId,
                    new PutToNestedScriptBuilder()).export();
        }
        return super.getAdditionalData();
    }

}
