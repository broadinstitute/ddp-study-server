package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;


/**
 * General-purpose utility methods used to work with collections
 */
public class CollectionMiscUtil {

    /**
     * Create list of {@link T} from list of {@link S}.
     *
     * @param srcElemList         - List of source elements
     * @param srcToTgtTransformer - transformer function from {@link S} to {@link T}
     * @param <S>                 - source element type
     * @param <T>                 - target element type
     * @return List of TGT_ELEM
     */
    public static <S, T> List<T> createListFromAnotherList(List<S> srcElemList, Function<S, T> srcToTgtTransformer) {
        List<T> tgtElemList = new ArrayList<>();
        if (srcElemList != null) {
            srcElemList.forEach(srcElem -> {
                var elem = srcToTgtTransformer.apply(srcElem);
                if (elem != null) {
                    tgtElemList.add(elem);
                }
            });
        }
        return tgtElemList;
    }

    /**
     * Add specified values to a specified set: only non-null values are added
     *
     * @param valuesSet set where to add values
     * @param values    values list
     */
    public static <T> Set<T> addNonNullsToSet(Set<T> valuesSet, T... values) {
        for (T value : values) {
            if (value != null) {
                valuesSet.add(value);
            }
        }
        return valuesSet;
    }

    /**
     * Consume non-null values (from a specified list) by a specified consumer
     *
     * @param consumer consumer which consumes values
     * @param values   values list
     */
    public static <T> void consumeNonNulls(Consumer<T> consumer, T... values) {
        for (T value : values) {
            if (value != null) {
                consumer.accept(value);
            }
        }
    }

    public static Collector<CharSequence, ?, String> joinWithComma() {
        return Collectors.joining(",");
    }

    public static boolean startsWithAny(String str, List<String> prefixes) {
        if (str != null) {
            return prefixes
                    .stream()
                    .anyMatch(prefix -> StringUtils.startsWith(str, prefix));
        }
        return false;
    }
}
