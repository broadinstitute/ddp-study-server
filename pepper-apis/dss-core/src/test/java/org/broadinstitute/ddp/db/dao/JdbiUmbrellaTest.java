package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.UmbrellaDto;
import org.junit.Test;

public class JdbiUmbrellaTest extends TxnAwareBaseTest {

    private void deleteUmbrella(UmbrellaDto umbrellaDto) {
        int rowsDeleted = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUmbrella.class).deleteById(umbrellaDto.getId())
        );
        assertEquals(1, rowsDeleted);
    }

    @Test
    public void testInsertAndFindUmbrellaDto() {
        UmbrellaDto testUmbrellaDto = new UmbrellaDto(-1, "Fake Umbrella", "fake umbrella");

        Optional<UmbrellaDto> optionalResultUmbrellaDto = TransactionWrapper.withTxn(handle -> {
            JdbiUmbrella jdbiUmbrella = handle.attach(JdbiUmbrella.class);

            jdbiUmbrella.insert(testUmbrellaDto.getName(), testUmbrellaDto.getGuid());

            return jdbiUmbrella.findByGuid(testUmbrellaDto.getGuid());
        });

        assertTrue(optionalResultUmbrellaDto.isPresent());
        UmbrellaDto resultUmbrellaDto = optionalResultUmbrellaDto.get();
        assertEquals(testUmbrellaDto.getName(), resultUmbrellaDto.getName());
        assertEquals(testUmbrellaDto.getGuid(), resultUmbrellaDto.getGuid());

        deleteUmbrella(resultUmbrellaDto);
    }
}
