package org.broadinstitute.dsm.model.elastic.filter.splitter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateSplitterStrategy extends EqualsSplitterStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DateSplitterStrategy.class);

    // DATE(FROM_UNIXTIME(k.scan_date/1000))  = DATE(FROM_UNIXTIME(1640563200))
    @Override
    public String[] split() {
        String[] equalSeparated = super.split();
        String leftSide = equalSeparated[0].trim();
        String rightSide = equalSeparated[1].trim();
        String fieldWithAlias =
                leftSide.split(Util.ESCAPE_CHARACTER + Filter.OPEN_PARENTHESIS)[2].split(Util.FORWARD_SLASH_SEPARATOR)[0];
        String value = rightSide.split(Util.ESCAPE_CHARACTER + Filter.OPEN_PARENTHESIS)[2].split(
                Util.ESCAPE_CHARACTER + Filter.CLOSE_PARENTHESIS)[0];
        try {
            Instant valueInstance = Instant.ofEpochMilli(Long.parseLong(value) * 1000);
            return new String[] {fieldWithAlias, DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC")).format(valueInstance)};
        } catch (NumberFormatException e) {
            logger.error("DateSplitterStrategy wasn't able to parse " + value + " into long");
        }
        return new String[] {fieldWithAlias, value};
    }

}
