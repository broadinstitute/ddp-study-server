package org.broadinstitute.dsm.model.participant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.stream.Stream;

import org.junit.Test;

public class UtilTest {

    @Test
    public void isUnderDsmKey() {
        String aliasM = "m";
        String aliasP = "p";
        String aliasC = "c";
        String aliasOd = "oD";
        boolean allMatch = Stream.of(aliasC, aliasM, aliasOd, aliasP)
                .allMatch(Util::isUnderDsmKey);
        assertTrue(allMatch);
        String noAlias = "noAlias";
        assertFalse(Util.isUnderDsmKey(noAlias));
    }
}
