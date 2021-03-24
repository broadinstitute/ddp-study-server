package org.broadinstitute.ddp.datstat;

import java.util.ArrayList;
import java.util.List;

/**
 * This Enum translates between DSM field names to DatStat
 * (or if we need other survey we might use) data fields
 */
public enum ParticipantFields
{

    ID(DatStatUtil.ALTPID_FIELD, "participantId"),
    FIRST_NAME("DATSTAT_FIRSTNAME", "firstName"),
    LAST_NAME("DATSTAT_LASTNAME", "lastName"),
    EMAIL("DATSTAT_EMAIL", "email"),
    VALID_ADDRESS("DDP_ADDRESS_VALID", "validAddress"),
    STREET1("DDP_STREET1", "street1"),
    STREET2("DDP_STREET2", "street2"),
    CITY("DDP_CITY", "city"),
    STATE("DDP_STATE", "state"),
    POSTAL_CODE("DDP_POSTAL_CODE", "postalCode"),
    COUNTRY("DDP_COUNTRY", "country"),
    SHORT_ID("DDP_PARTICIPANT_SHORTID", "shortId");

    private String DatStatValue;
    private String DSMValue;

    ParticipantFields(String datStat, String dsm)
    {
        this.DatStatValue = datStat;
        this.DSMValue = dsm;
    }

    public String getDSMValue()
    {
        return this.DSMValue;
    }

    public String getDatStatValue()
    {
        return this.DatStatValue;
    }

    public ParticipantFields findByDSMValue(String dsm)
    {
        for (ParticipantFields field : ParticipantFields.values())
        {
            if (field.DSMValue.equals((dsm)))
            {
                return field;
            }
        }
        return null;
    }

    public ParticipantFields findByDatStatValue(String datStat)
    {
        for (ParticipantFields field : ParticipantFields.values())
        {
            if (field.DatStatValue.equals((datStat)))
            {
                return field;
            }
        }
        return null;
    }

    public static List<String> getAllDatStatValues()
    {
        List<String> datStatFields = new ArrayList<>();

        for (ParticipantFields field : ParticipantFields.values())
        {
            datStatFields.add(field.getDatStatValue());
        }
        return datStatFields;
    }
}
