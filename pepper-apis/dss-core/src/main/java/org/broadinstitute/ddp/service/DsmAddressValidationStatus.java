package org.broadinstitute.ddp.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

public enum DsmAddressValidationStatus {
    DSM_INVALID_ADDRESS_STATUS(0, "INVALID"),
    DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS(1, "SUGGESTED"),
    DSM_VALID_ADDRESS_STATUS(2, "VALID");

    private final int code;
    private final String shortName;

    DsmAddressValidationStatus(int code, String shortName) {
        this.code = code;
        this.shortName = shortName;
    }

    public static DsmAddressValidationStatus getByCode(int code) throws Exception {
        for (DsmAddressValidationStatus e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new Exception("Unknown code for DsmAddressValidationStatus");
    }

    public static Set<DsmAddressValidationStatus> addressValidStatuses() {
        return new HashSet<>(Arrays.asList(DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS, DSM_VALID_ADDRESS_STATUS));
    }

    public int getCode() {
        return code;
    }

    public String getShortName() {
        return shortName;
    }

    public static class ByOrdinalColumnMapper implements ColumnMapper<DsmAddressValidationStatus> {
        @Override
        public DsmAddressValidationStatus map(ResultSet rs, int col, StatementContext ctx) throws SQLException {
            try {
                return getByCode(rs.getInt(col));
            } catch (Exception e) {
                throw new SQLException("could not convert address status enum by ordinal", e);
            }
        }
    }
}
