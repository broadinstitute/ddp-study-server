package org.broadinstitute.dsm.model.defaultvalues;

public class DefaultableMaker {

    public static Defaultable makeDefaultable(String studyGuid) {
        Defaultable defaultable = null;
        // TODO: need to get these study guid constants from a shared source of truth.
        //  See https://broadworkbench.atlassian.net/browse/PEPPER-1067 -DC
        switch (studyGuid.toUpperCase()) {
            case "CMI-OSTEO":
                defaultable = new NewOsteoDefaultValues();
                break;
            case "RGP":
                defaultable = new RgpAutomaticProbandDataCreator();
                break;
            default:
                break;
        }
        return defaultable;
    }

}
