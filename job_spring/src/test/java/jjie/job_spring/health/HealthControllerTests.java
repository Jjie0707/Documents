package jjie.job_spring.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import jjie.job_spring.config.SecurityConfig;
import jjie.job_spring.health.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousHealthCheckValidatesAndClosesConnection() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
        verify(connection).close();
    }

    @Test
    void invalidConnectionReturnsServiceUnavailable() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.database").value("DOWN"));
        verify(connection).close();
    }

    @Test
    void databaseFailureDoesNotExposeExceptionDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("private-connection-details"));

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(content().string(not(containsString("private-connection-details"))));
    }

    @Test
    void otherEndpointsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
    }
}
