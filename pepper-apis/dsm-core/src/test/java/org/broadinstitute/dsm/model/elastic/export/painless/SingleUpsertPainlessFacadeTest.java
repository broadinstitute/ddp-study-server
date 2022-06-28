package org.broadinstitute.dsm.model.elastic.export.painless;

import static org.junit.Assert.assertEquals;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
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
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        upsertPainlessFacade = new SingleUpsertPainlessFacade(participant, ddpInstanceDto, "participantId", "participantId", 10L,
                new MockFieldTypeExtractor(), new AddToSingleScriptBuilder());
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
