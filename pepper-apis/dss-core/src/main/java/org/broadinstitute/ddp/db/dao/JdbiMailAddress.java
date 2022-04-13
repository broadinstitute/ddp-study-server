package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
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

    @SqlQuery("findNonDefaultAddressesByParticipantIds")
    @UseRowReducer(BulkFindNonDefaultMailAddressesReducer.class)
    @UseStringTemplateSqlLocator
    Stream<NonDefaultMailAddressesWrapper> findNonDefaultAddressesByParticipantIds(
            @BindList(value = "participantGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> participantGuids);

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


    /**
     * Reducer for reading non-default MailAddresses of a specified set of Participants
     */
    class BulkFindNonDefaultMailAddressesReducer implements LinkedHashMapRowReducer<String, NonDefaultMailAddressesWrapper> {

        @Override
        public void accumulate(Map<String, NonDefaultMailAddressesWrapper> container, RowView view) {
            try {
                String participantGuid = view.getColumn("participant_guid", String.class);
                MailAddress mailAddress = new MailAddress(
                        view.getColumn("address_id", Long.class),
                        view.getColumn("address_guid", String.class),
                        view.getColumn("name", String.class),
                        view.getColumn("street1", String.class),
                        view.getColumn("street2", String.class),
                        view.getColumn("city", String.class),
                        view.getColumn("state", String.class),
                        view.getColumn("country", String.class),
                        view.getColumn("zip", String.class),
                        view.getColumn("phone", String.class),
                        view.getColumn("pluscode", String.class),
                        view.getColumn("description", String.class),
                        DsmAddressValidationStatus.getByCode(view.getColumn("validationStatus", Integer.class)),
                        false
                );
                container.computeIfAbsent(participantGuid, NonDefaultMailAddressesWrapper::new).unwrap().add(mailAddress);
            } catch (Exception e) {
                throw new DaoException("Error during parsing a DB result with non-default MailAddresses ", e);
            }
        }
    }

    /**
     * A wrapper around a non-default MailAddress'es of a certain Participant,
     */
    class NonDefaultMailAddressesWrapper {

        private String participantGuid;
        private List<MailAddress> mailAddresses = new ArrayList<>();

        public NonDefaultMailAddressesWrapper(String participantGuid) {
            this.participantGuid = participantGuid;
        }

        public String getParticipantGuid() {
            return participantGuid;
        }

        public List<MailAddress> unwrap() {
            return mailAddresses;
        }
    }
}
