package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.DiagnosisCodeDto;
import me.shail.model.DiagnosisCode;
import me.shail.repository.DiagnosisCodeRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisCodeServiceTest {

    @Mock
    DiagnosisCodeRepository diagnosisCodeRepository;

    @InjectMocks
    DiagnosisCodeService service;

    @Test
    void findByIdUsesTheQueryThatFetchesTheGroup() {
        DiagnosisCode code = TestData.diagnosisCode(TestData.conditionGroup((short) 2));
        when(diagnosisCodeRepository.findWithGroup(code.icdCode)).thenReturn(Uni.createFrom().item(code));

        DiagnosisCodeDto dto = service.findById(code.icdCode).await().indefinitely();

        assertEquals(DiagnosisCodeDto.from(code), dto);
        assertEquals("E11", dto.conditionGroup().icdPrefix());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(diagnosisCodeRepository.findWithGroup("Z99.9")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findById("Z99.9").await().indefinitely());
    }

    @Test
    void findAllMapsEveryCode() {
        DiagnosisCode chronic = TestData.diagnosisCode(TestData.conditionGroup((short) 2));
        DiagnosisCode other = TestData.diagnosisCode(null);
        when(diagnosisCodeRepository.findAllWithGroup()).thenReturn(Uni.createFrom().item(List.of(chronic, other)));

        assertEquals(List.of(DiagnosisCodeDto.from(chronic), DiagnosisCodeDto.from(other)),
                service.findAll().await().indefinitely());
    }

    @Test
    void findChronicMapsEveryCode() {
        DiagnosisCode chronic = TestData.diagnosisCode(TestData.conditionGroup((short) 2));
        when(diagnosisCodeRepository.findChronic()).thenReturn(Uni.createFrom().item(List.of(chronic)));

        assertEquals(List.of(DiagnosisCodeDto.from(chronic)), service.findChronic().await().indefinitely());
    }
}
