package org.broadinstitute.dsm.model.rgp;

import static org.broadinstitute.dsm.TestHelper.setupDB;

import java.util.Map;

import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class AutomaticProbandDataCreatorTest {


    @BeforeClass
    public static void before() throws Exception {
        setupDB();
    }

    @Test
    public void insertFamilyIdToEs() {
        int familyId = 999;
        String participantId = "S3POBS0P9X0MB41FT1JZ";
        String esIndex = "participants_structured.rgp.rgp";
        new AutomaticProbandDataCreator().insertFamilyIdToDsmES(esIndex, participantId,
                familyId);
        int familyIdFromEs = 0;
        try {
            Map<String, Object> esObjectsMap = ElasticSearchUtil.getObjectsMap(esIndex, participantId, ESObjectConstants.DSM);
            Map<String, Object> dsmObjectMap = (Map<String, Object>) esObjectsMap.get(ESObjectConstants.DSM);
            familyIdFromEs = (Integer) dsmObjectMap.get(ESObjectConstants.FAMILY_ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Assert.assertEquals(familyId, familyIdFromEs);
    }

}
