package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class ColumnValue {
    private Collection<?> object;
    private final Alias alias;

    public ColumnValue(Alias alias, Collection<?> object) {
        this.object = object;
        this.alias = alias;
    }
    
    public int getColumnsSize() {
        return object.size();
    }

    public Alias getAlias() {
        return alias;
    }

    public Iterator<?> iterator() {
        return object.iterator();
    }

    public void appendEmptyStrings(int size) {
        this.object = Stream.concat(object.stream(), IntStream.range(0, size).mapToObj(s -> StringUtils.EMPTY))
                .collect(Collectors.toList());
    }

}
