package org.broadinstitute.dsm.model.elastic.export.painless;

import static org.junit.Assert.*;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.Before;
import org.junit.Test;

public class SingleUpsertPainlessFacadeTest {

    UpsertPainlessFacade upsertPainlessFacade;

    @Before
    public void setUp() {
        Participant participant = new Participant();
        participant.setParticipantId(10L);
        participant.setMinimalMr(true);
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                .build();
        upsertPainlessFacade = new SingleUpsertPainlessFacade(
                participant, ddpInstanceDto, "participantId", "participantId",10L, new MockFieldTypeExtractor()
        );
    }

    @Test
    public void buildScriptBuilder() {
        assertTrue(upsertPainlessFacade.buildScriptBuilder() instanceof SingleScriptBuilder);
    }

    @Test
    public void buildQueryBuilder() {
        QueryBuilder queryBuilder = upsertPainlessFacade.buildQueryBuilder();
        BoolQueryBuilder expected = new BoolQueryBuilder();
        TermQueryBuilder term = new TermQueryBuilder("dsm.participant.participantId", 10L);
        expected.must(term);
        assertEquals(expected, queryBuilder);
    }
}