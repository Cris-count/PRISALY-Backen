package com.prisaly.m3.presentation;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.service.CasoApplicationService;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Error translation through real Spring/Hibernate wrappers and JDBC exception chains. */
class CasoCreationSqlErrorsTest {
    private final UUID owner = UUID.randomUUID();
    private final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    private final CaseAuthorizationPort authorization = mock(CaseAuthorizationPort.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasoCreationController(
            new CasoApplicationService(authorization, repository), () -> owner))
            .setControllerAdvice(new CasoCreationExceptionHandler()).build();

    private void expectFailure(SQLException sql, int status, String code) throws Exception {
        when(authorization.canCreateCase(owner.toString(), owner.toString())).thenReturn(true);
        doThrow(new DataIntegrityViolationException("private parameters",
                new GenericJDBCException("private driver", sql, "INSERT private SQL")))
                .when(repository).insert(any());
        mvc.perform(post("/v1/casos").contentType("application/json").content(
                "{\"idCaso\":\"" + UUID.randomUUID() + "\",\"titulo\":\"Draft\",\"tiempoEstimadoSegundos\":300}"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        verify(repository).insert(any());
    }

    @Test
    void uniqueViolationInNextSqlExceptionRetainsConflictThroughSpringAndHibernate() throws Exception {
        // JDBC may carry the concrete server failure in nextException, not in Throwable.cause.
        var batch = new BatchUpdateException("private batch", null, 0, new int[0]);
        batch.setNextException(new SQLException("private constraint", "23505"));
        expectFailure(batch, 409, "CASE_CONFLICT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"08006", "23503", "HY000"})
    void technicalReferentialOrUnknownSqlFailureMustNotBecomeConflictBecauseItsCauseIsDuplicate(String state)
            throws Exception {
        var failure = new SQLException("private operation failure", state,
                new SQLException("private earlier constraint", "23505"));
        expectFailure(failure, 500, "CASE_CREATION_FAILED");
    }
}
