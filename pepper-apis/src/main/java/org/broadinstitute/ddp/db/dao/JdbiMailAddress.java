package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiMailAddress extends SqlObject {
    String MAIL_ADDRESS_TABLE = "mailing_address";
    String ADDRESS_GUID_COLUMN = "address_guid";

    @SqlUpdate("deleteAddressById")
    @UseStringTemplateSqlLocator
    boolean deleteAddress(long addressId);

    @SqlUpdate("deleteAddressByGuid")
    @UseStringTemplateSqlLocator
    boolean deleteAddressByGuid(String guid);

    @SqlUpdate("insertAddress")
    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    long insertAddress(@Bind("guid") String addressGuid, @BindBean("a") MailAddress address,
                       @Bind("participantGuid") String participantGuid,
                       @Bind("operatorGuid") String creatorGuid,
                       @Bind("creationTime") long creationTime);

    default MailAddress insertAddress(MailAddress address, String participantGuid, String creatorGuid) {
        String guid = DBUtils.uniqueStandardGuid(getHandle(), MAIL_ADDRESS_TABLE, ADDRESS_GUID_COLUMN);
        long id = insertAddress(guid, address, participantGuid, creatorGuid, Instant.now().getEpochSecond());
        address.setGuid(guid);
        address.setId(id);
        return address;
    }

    default MailAddress insertLegacyAddress(MailAddress address, String participantGuid, String creatorGuid, long creationTime) {
        String guid = DBUtils.uniqueStandardGuid(getHandle(), MAIL_ADDRESS_TABLE, ADDRESS_GUID_COLUMN);
        long id = insertAddress(guid, address, participantGuid, creatorGuid, creationTime);
        address.setGuid(guid);
        address.setId(id);
        return address;
    }

    @SqlQuery("findAddressByGuid")
    @RegisterBeanMapper(MailAddress.class)
    @UseStringTemplateSqlLocator
    Optional<MailAddress> findAddressByGuid(String addressGuid);

    @SqlQuery("findDefaultAddressForParticipantGuid")
    @RegisterBeanMapper(MailAddress.class)
    @UseStringTemplateSqlLocator
    Optional<MailAddress> findDefaultAddressForParticipant(String participantGuid);

    @SqlUpdate("updateAddress")
    @UseStringTemplateSqlLocator
    int updateAddress(@Bind("guid") String guid, @BindBean("a") MailAddress addressWithUpdatedFields,
                      @Bind("participantGuid") String participantGuid, @Bind("operatorGuid") String creatorGuid,
                      @Bind("updateTime") long updateTime);

    default int updateAddress(String guid, MailAddress addressWithUpdatedFields,
                              String participantGuid, String creatorGuid) {
        return updateAddress(guid, addressWithUpdatedFields, participantGuid,
                creatorGuid, Instant.now().getEpochSecond());
    }

    @SqlQuery("findAllAddressesByParticipantGuid")
    @RegisterBeanMapper(MailAddress.class)
    @UseStringTemplateSqlLocator
    List<MailAddress> findAllAddressesForParticipant(String participantGuid);

    @SqlUpdate("setAddressAsDefault")
    @UseStringTemplateSqlLocator
    void setDefaultAddressForParticipant(@Bind("guid") String mailAddressGuid);

    @SqlUpdate("unsetAddressAsDefault")
    @UseStringTemplateSqlLocator
    int unsetDefaultAddressForParticipant(@Bind("guid") String mailAddressGuid);

    @SqlUpdate("deleteAddressByParticipantId")
    @UseStringTemplateSqlLocator
    boolean deleteAddressByParticipantId(@Bind("participantId") Long participantId);

    @SqlUpdate("deleteDefaultAddressByParticipantId")
    @UseStringTemplateSqlLocator
    boolean deleteDefaultAddressByParticipantId(@Bind("participantId") Long participantId);

}
