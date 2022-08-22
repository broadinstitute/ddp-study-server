package org.broadinstitute.dsm.model.participant;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class Util {

    private static final List<Alias> aliases = Arrays.asList(
            Alias.P, Alias.M, Alias.OD, Alias.K, Alias.T, Alias.O, Alias.D, Alias.R, Alias.C, Alias.CL
    );

    public static boolean isUnderDsmKey(String alias) {
        try {
            return aliases.contains(Alias.valueOf(alias.toUpperCase()));
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }
}
