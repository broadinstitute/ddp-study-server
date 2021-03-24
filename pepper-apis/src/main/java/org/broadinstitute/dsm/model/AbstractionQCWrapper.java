package org.broadinstitute.dsm.model;

import lombok.Data;
import org.broadinstitute.dsm.db.AbstractionFieldValue;

@Data
public class AbstractionQCWrapper {

    private AbstractionFieldValue abstraction; // contains all the abstraction field values
    private AbstractionFieldValue review; // contains all the review field values

    private Boolean equals;
    private Boolean check;

    public AbstractionQCWrapper(AbstractionFieldValue abstraction, AbstractionFieldValue review,
                                Boolean equals, Boolean check) {
        this.abstraction = abstraction;
        this.review = review;
        this.equals = equals;
        this.check = check;
    }
}
