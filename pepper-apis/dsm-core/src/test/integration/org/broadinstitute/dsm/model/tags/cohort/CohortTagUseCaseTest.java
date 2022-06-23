package org.broadinstitute.dsm.model.tags.cohort;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.broadinstitute.dsm.model.elastic.export.painless.AddListToNestedByGuidScriptBuilder;
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
    public static final String GUID2 = "TEST0000000000000999";
    static CohortTag cohortTagPayload;
    static DDPInstanceDto ddpInstanceDto;
    static CohortTagDao cohortTagDao;
    static DDPInstanceDao ddpInstanceDao;
    static ElasticSearchable elasticSearchable;
    static List<Integer> bulkCreatedCohortTagsIds;

    @BeforeClass
    public static void beforeAll() {
        TestHelper.setupDB();
        ddpInstanceDao = new DDPInstanceDao();
        cohortTagDao = new CohortTagDaoImpl();
        elasticSearchable = new ElasticSearch();
        bulkCreatedCohortTagsIds = new ArrayList<>();
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
        if (bulkCreatedCohortTagsIds.size() > 0) {
            bulkCreatedCohortTagsIds.forEach(cohortTagDao::delete);
            elasticSearchable.deleteDocumentById(ddpInstanceDto.getEsParticipantIndex(), GUID);
            elasticSearchable.deleteDocumentById(ddpInstanceDto.getEsParticipantIndex(), GUID2);
        }
    }

    @Test
    public void insertIfCohortNotExists() {
        elasticSearchable.createDocumentById(
                ddpInstanceDto.getEsParticipantIndex(), GUID,
                buildMapWithDsmAndProfileGuid(GUID)
        );
        try {
            waitForCreationInElasticSearch();
            getCohortTagUseCase(new PutToNestedScriptBuilder()).insert();
            ElasticSearchParticipantDto elasticSearchParticipantDto = getElasticSearchParticipantDto(GUID);
            Assert.assertEquals(1, elasticSearchParticipantDto.getDsm().get().getCohortTag().size());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void insertIfCohortExists() {
        createCohortInESWithExistingTag();
        try {
            waitForCreationInElasticSearch();
            getCohortTagUseCase(new PutToNestedScriptBuilder()).insert();
            ElasticSearchParticipantDto elasticSearchParticipantDto = getElasticSearchParticipantDto(GUID);
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
            ElasticSearchParticipantDto participantDto = getElasticSearchParticipantDto(GUID);
            Assert.assertTrue(participantDto.getDsm().get().getCohortTag().isEmpty());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void bulkCohortCreate() {
        elasticSearchable.createDocumentById(
                ddpInstanceDto.getEsParticipantIndex(), GUID,
                buildMapWithDsmAndProfileGuid(GUID)
        );
        elasticSearchable.createDocumentById(
                ddpInstanceDto.getEsParticipantIndex(), GUID2,
                buildMapWithDsmAndProfileGuid(GUID2)
        );
        try {
            waitForCreationInElasticSearch();
            CohortTagUseCase cohortTagUseCase = getCohortTagUseCase(new AddListToNestedByGuidScriptBuilder());
            cohortTagUseCase.setCohortTagPayload(null);

            BulkCohortTag bulkCohortTag = new BulkCohortTag(
                    Arrays.asList(COHORT_TAG_NAME + 1, COHORT_TAG_NAME + 2), Arrays.asList(GUID, GUID2)
            );
            cohortTagUseCase.setBulkCohortTag(bulkCohortTag);
            bulkCreatedCohortTagsIds.addAll(cohortTagUseCase.bulkInsert());

            ElasticSearchParticipantDto elasticSearchParticipantDto = getElasticSearchParticipantDto(GUID);
            ElasticSearchParticipantDto elasticSearchParticipantDto2 = getElasticSearchParticipantDto(GUID2);

            Assert.assertEquals(2, elasticSearchParticipantDto.getDsm().get().getCohortTag().size());
            Assert.assertEquals(2, elasticSearchParticipantDto2.getDsm().get().getCohortTag().size());
            Assert.assertEquals(
                    COHORT_TAG_NAME + 1, elasticSearchParticipantDto2.getDsm().get().getCohortTag().get(0).getCohortTagName()
            );
        } catch (Exception e) {
            Assert.fail(e.toString());
        }

    }



    static Map<String, Object> buildMapWithDsmAndProfileGuid(String guid) {
        return Map.of(
                ESObjectConstants.DSM, Map.of(),
                ElasticSearchUtil.PROFILE, Map.of(ESObjectConstants.GUID, guid)
        );
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

    private ElasticSearchParticipantDto getElasticSearchParticipantDto(String guid) {
        ElasticSearchParticipantDto participantDto =
                elasticSearchable.getParticipantById(ddpInstanceDto.getEsParticipantIndex(), guid);
        return participantDto;
    }

    static void waitForCreationInElasticSearch() throws InterruptedException {
        Thread.sleep(1000);
    }

}
