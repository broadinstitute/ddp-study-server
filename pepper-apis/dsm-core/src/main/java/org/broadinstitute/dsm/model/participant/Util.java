package org.broadinstitute.dsm.model.participant;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class Util {

    private static final List<Alias> aliases = Arrays.asList(
            Alias.P, Alias.M, Alias.OD, Alias.K, Alias.T, Alias.O, Alias.D, Alias.R, Alias.C, Alias.CL, Alias.SM, Alias.PARTICIPANTDATA
    );

    public static boolean isUnderDsmKey(String alias) {
        try {
            return aliases.contains(Alias.valueOf(alias.toUpperCase()));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isUnderDsmKey(Alias alias) {
        return aliases.contains(alias);
    }
}
