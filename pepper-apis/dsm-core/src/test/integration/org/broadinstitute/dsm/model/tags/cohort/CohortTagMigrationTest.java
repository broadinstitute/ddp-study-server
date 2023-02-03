package org.broadinstitute.dsm.model.tags.cohort;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.Util;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.migration.CohortTagMigrator;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class CohortTagMigrationTest {
    private static List<CohortTag> tagsToCreate;
    private static Map<String, List<CohortTag>> cohortTagsByParticipantId;
    static CohortTagDao cohortTagDao;
    static ElasticSearchable elasticSearchable;
    static DDPInstanceDto ddpInstanceDto;

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
        cohortTagDao = new CohortTagDaoImpl();
        elasticSearchable = new ElasticSearch();
        ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(CohortTagUseCaseTest.INSTANCE_NAME).orElseThrow();
        elasticSearchable.createDocumentById(
                ddpInstanceDto.getEsParticipantIndex(), CohortTagUseCaseTest.GUID,
                CohortTagUseCaseTest.buildMapWithDsmAndProfileGuid(CohortTagUseCaseTest.GUID)
        );
        tagsToCreate = getCohortTagsByParticipantId();
        createCohortTagsAndSetPks(tagsToCreate);
        cohortTagsByParticipantId = cohortTagDao.getCohortTagsByInstanceName(CohortTagUseCaseTest.INSTANCE_NAME);
    }

    @AfterClass
    public static void finish() {
        for (CohortTag tag: tagsToCreate) {
            cohortTagDao.delete(tag.getCohortTagId());
        }
        elasticSearchable.deleteDocumentById(ddpInstanceDto.getEsParticipantIndex(), CohortTagUseCaseTest.GUID);
    }

    private static void createCohortTagsAndSetPks(List<CohortTag> tagsToCreate) {
        tagsToCreate = new CopyOnWriteArrayList<>(tagsToCreate);
        for (CohortTag tag: tagsToCreate) {
            tag.setCohortTagId(cohortTagDao.create(tag));
        }
    }

    private static List<CohortTag> getCohortTagsByParticipantId() {

        CohortTag cohortTag = new CohortTag();
        cohortTag.setCohortTagName(CohortTagUseCaseTest.COHORT_TAG_NAME + 1);
        cohortTag.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
        cohortTag.setDdpParticipantId(CohortTagUseCaseTest.GUID);

        CohortTag cohortTag2 = new CohortTag();
        cohortTag2.setCohortTagName(CohortTagUseCaseTest.COHORT_TAG_NAME + 2);
        cohortTag2.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
        cohortTag2.setDdpParticipantId(CohortTagUseCaseTest.GUID);

        return Arrays.asList(cohortTag, cohortTag2);
    }


    @Test
    @Ignore
    public void export() {
        CohortTagMigrator cohortTagMigrator = new CohortTagMigrator(
                ddpInstanceDto.getEsParticipantIndex(), CohortTagUseCaseTest.INSTANCE_NAME, cohortTagDao
        );
        cohortTagMigrator.export();
        try {
            Util.waitForCreationInElasticSearch();
            ElasticSearchParticipantDto participantById =
                    elasticSearchable.getParticipantById(ddpInstanceDto.getEsParticipantIndex(), CohortTagUseCaseTest.GUID);
            List<CohortTag> cohortTags = participantById.getDsm().get().getCohortTag();
            Assert.assertEquals(cohortTags, tagsToCreate);
        } catch (Exception e) {
            Assert.fail();
        }
    }

}
