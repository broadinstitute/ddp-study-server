package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CollectionMiscUtil {

    /**
     * Create list of {@link T} from list of {@link S}.
     * @param srcElemList - List of source elements
     * @param srcToTgtTransformer - transformer function from {@link S} to {@link T}
     * @param <S> - source element type
     * @param <T> - target element type
     * @return List of TGT_ELEM
     */
    public static <S, T> List<T> createListFromAnotherList(List<S> srcElemList, Function<S, T> srcToTgtTransformer) {
        List<T> tgtElemList = new ArrayList<>();
        if (srcElemList != null) {
            srcElemList.forEach(srcElem -> tgtElemList.add(srcToTgtTransformer.apply(srcElem)));
        }
        return tgtElemList;
    }
}
