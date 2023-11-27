package org.broadinstitute.ddp.util.gen2.enums;

import java.util.Arrays;

public enum LovedOneRelationTo {
    PARENT(1),
    SIBLING(2),
    CHILD(3),
    AUNT_UNCLE(4),
    SPOUSE(7),
    FRIEND(5),
    OTHER(6);

    private final int datStatEnumValue;

    LovedOneRelationTo(int datStatEnumValue) {
        this.datStatEnumValue = datStatEnumValue;
    }

    public int getDatStatEnumValue() {
        return datStatEnumValue;
    }

    public static LovedOneRelationTo fromDatStatEnum(Integer datStatEnumValue) throws Exception {
        if (datStatEnumValue == null) {
            return null;
        } else {
            return Arrays.stream(values())
                    .filter(lor -> lor.datStatEnumValue == datStatEnumValue)
                    .findFirst()
                    .orElseThrow(() -> new Exception("No LovedOne RelationTo Mapping for value " + datStatEnumValue));
        }
    }
}
