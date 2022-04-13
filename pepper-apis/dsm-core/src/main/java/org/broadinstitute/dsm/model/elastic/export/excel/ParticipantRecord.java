package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class ParticipantRecord {
    private final List<ColumnValue> values = new ArrayList<>();
    public List<String> transposeAndFlatten(List<Integer> columnSizes) {
        fillWithEmptyStringsIfNeeded(columnSizes);
        final int size = values.stream().mapToInt(ColumnValue::getColumnsSize).max().orElse(-1);
        Map<Alias, List<Iterator<?>>> aliasIterators = new LinkedHashMap<>();
        values.stream().map(columnValue -> Map.entry(columnValue.getAlias(), columnValue.iterator()))
                .forEach(entry -> aliasIterators.computeIfAbsent(entry.getKey(), e -> new ArrayList<>()).add(entry.getValue()));
        Stream.Builder<String> rowStream = Stream.builder();
        aliasIterators.forEach((key, value) -> {
            if (key == Alias.ACTIVITIES) {
                value.forEach(iter -> {
                    while (iter.hasNext()) {
                        rowStream.add(iter.next().toString());
                    }
                });
            } else {
                IntStream.range(0, size)
                        .mapToObj(n -> value.stream()
                                .filter(Iterator::hasNext)
                                .map(it -> it.next().toString())
                                .collect(Collectors.toList()))
                        .flatMap(Collection::stream).forEach(rowStream::add);
            }
        });
        return rowStream.build().collect(Collectors.toList());
    }

    private void fillWithEmptyStringsIfNeeded(List<Integer> columnSizes) {
        for (int i = 0; i < values.size(); i++) {
            int currentSize = values.get(i).getColumnsSize();
            int requiredSize = columnSizes.get(i);
            if (currentSize < requiredSize) {
                values.get(i).appendEmptyStrings(requiredSize - currentSize);
            }
        }
    }

    public void add(ColumnValue columnValue) {
        values.add(columnValue);
    }

    public List<ColumnValue> getValues() {
        return values;
    }

}
