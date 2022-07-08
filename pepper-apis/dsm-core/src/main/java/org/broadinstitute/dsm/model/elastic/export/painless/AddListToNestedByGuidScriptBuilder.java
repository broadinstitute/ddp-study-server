package org.broadinstitute.dsm.model.elastic.export.painless;

public class AddListToNestedByGuidScriptBuilder extends NestedScriptBuilder {

    private static final String SCRIPT = "if (ctx._source.dsm.#propertyName == null) { ArrayList listToAdd = new ArrayList(); "
            + "for(property in params.dsm.#propertyName) "
            + "{ if (ctx._source.profile.guid == property.#uniqueIdentifier) { listToAdd.add(property); } } "
            + "ctx._source.dsm.#propertyName = listToAdd; } else { ArrayList listToAdd = new ArrayList(); "
            + "for(property in params.dsm.#propertyName) "
            + "{ if (ctx._source.profile.guid == property.#uniqueIdentifier) { listToAdd.add(property); } } "
            + "ctx._source.dsm.#propertyName.addAll(listToAdd) }";

    public AddListToNestedByGuidScriptBuilder(String propertyName, String uniqueIdentifier) {
        super(propertyName, uniqueIdentifier, SCRIPT);
    }

    public AddListToNestedByGuidScriptBuilder() {
        super(SCRIPT);
    }

}
