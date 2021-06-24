package edu.harvard.iq.dataverse.ror;

import com.google.common.collect.Sets;
import edu.harvard.iq.dataverse.persistence.ror.RorData;
import edu.harvard.iq.dataverse.persistence.ror.RorDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RorTransactionsServiceTest {

    @Mock
    private RorDataRepository rorDataRepository;

    @InjectMocks
    private RorTransactionsService rorTransactionsService;

    @Test
    void truncateAll() {
        //when
        rorTransactionsService.truncateAll();

        //then
        Mockito.verify(rorDataRepository, Mockito.times(1)).truncateAll();
    }

    @Test
    void saveMany() {
        //when
        rorTransactionsService.saveMany(Sets.newHashSet(new RorData()));

        //then
        Mockito.verify(rorDataRepository, Mockito.times(1)).save(Mockito.any(RorData.class));
    }
}