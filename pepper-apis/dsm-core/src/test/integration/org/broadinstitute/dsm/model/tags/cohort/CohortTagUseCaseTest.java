package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.RemoveFromNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.ScriptBuilder;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class CohortTagUseCaseTest {

    public static final String COHORT_TAG_NAME = "TestTag";
    public static final String INSTANCE_NAME = "angio";
    public static final String GUID = "TEST0000000000009999";
    static CohortTag cohortTagPayload;
    static DDPInstanceDto ddpInstanceDto;
    static CohortTagDao cohortTagDao;
    static DDPInstanceDao ddpInstanceDao;
    static ElasticSearchable elasticSearchable;

    @BeforeClass
    public static void beforeAll() {
        TestHelper.setupDB();
        ddpInstanceDao = new DDPInstanceDao();
        cohortTagDao = new CohortTagDaoImpl();
        elasticSearchable = new ElasticSearch();
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(INSTANCE_NAME).orElse(new DDPInstanceDto.Builder().build());

        cohortTagPayload = new CohortTag();
        cohortTagPayload.setCohortTagName(COHORT_TAG_NAME);
        cohortTagPayload.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
        cohortTagPayload.setDdpParticipantId(GUID);
    }


    @After
    public void finish() {
        if (Objects.nonNull(cohortTagPayload.getCohortTagId())) {
            cohortTagDao.delete(cohortTagPayload.getCohortTagId());
            elasticSearchable.deleteDocumentById(ddpInstanceDto.getEsParticipantIndex(), GUID);
        }
    }

    @Test
    public void insertIfCohortNotExists() {
        elasticSearchable.createDocumentById(
                ddpInstanceDto.getEsParticipantIndex(), GUID,
                buildMapWithDsmAndProfileGuid()
        );
        try {
            waitForCreationInElasticSearch();
            getCohortTagUseCase(new PutToNestedScriptBuilder()).insert();
            ElasticSearchParticipantDto elasticSearchParticipantDto = getElasticSearchParticipantDto();
            Assert.assertEquals(1, elasticSearchParticipantDto.getDsm().get().getCohortTag().size());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    static Map<String, Object> buildMapWithDsmAndProfileGuid() {
        return Map.of(
                ESObjectConstants.DSM, Map.of(),
                ElasticSearchUtil.PROFILE, Map.of(ESObjectConstants.GUID, GUID)
        );
    }

    @Test
    public void insertIfCohortExists() {
        createCohortInESWithExistingTag();
        try {
            waitForCreationInElasticSearch();
            getCohortTagUseCase(new PutToNestedScriptBuilder()).insert();
            ElasticSearchParticipantDto elasticSearchParticipantDto = getElasticSearchParticipantDto();
            Assert.assertEquals(2, elasticSearchParticipantDto.getDsm().get().getCohortTag().size());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void delete() {
        cohortTagPayload.setCohortTagId(cohortTagDao.create(cohortTagPayload));
        createCohortInESWithExistingTag();
        try {
            waitForCreationInElasticSearch();
            getCohortTagUseCase(new RemoveFromNestedScriptBuilder()).delete();
            ElasticSearchParticipantDto participantDto = getElasticSearchParticipantDto();
            Assert.assertTrue(participantDto.getDsm().get().getCohortTag().isEmpty());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    private void createCohortInESWithExistingTag() {
        Map<String, Object> cohortTagAsMap =
                ObjectMapperSingleton.instance().convertValue(cohortTagPayload, new TypeReference<>() {
                });
        elasticSearchable.createDocumentById(
                ddpInstanceDto.getEsParticipantIndex(), GUID,
                Map.of(
                        ESObjectConstants.DSM, Map.of(ESObjectConstants.COHORT_TAG, List.of(cohortTagAsMap)),
                        ElasticSearchUtil.PROFILE, Map.of(ESObjectConstants.GUID, GUID)
                )
        );
    }

    private CohortTagUseCase getCohortTagUseCase(ScriptBuilder scriptBuilder) {
        return new CohortTagUseCase(
                cohortTagPayload, ddpInstanceDto, new CohortTagDaoImpl(),
                new ElasticSearch(), new NestedUpsertPainlessFacade(), scriptBuilder
        );
    }

    private ElasticSearchParticipantDto getElasticSearchParticipantDto() {
        ElasticSearchParticipantDto participantDto =
                elasticSearchable.getParticipantById(ddpInstanceDto.getEsParticipantIndex(), GUID);
        return participantDto;
    }

    static void waitForCreationInElasticSearch() throws InterruptedException {
        Thread.sleep(1000);
    }

}
