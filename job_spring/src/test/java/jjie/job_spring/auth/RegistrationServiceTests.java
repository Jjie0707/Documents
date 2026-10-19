package jjie.job_spring.auth;

import jjie.job_spring.user.common.UserAccount;
import jjie.job_spring.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationServiceTests {

    @Test
    void concurrentUniqueConstraintConflictBecomesUsernameConflict() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.exists(any())).thenReturn(false);
        when(encoder.encode(any())).thenReturn("test-hash-placeholder");
        when(mapper.insert(any(UserAccount.class))).thenThrow(new DuplicateKeyException("unique index"));

        RegistrationService service = new RegistrationService(mapper, encoder);
        assertThatThrownBy(() -> service.register(new RegisterRequest("learner", "test-only-password")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void knownDuplicateIsRejectedBeforeHashing() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> new RegistrationService(mapper, encoder)
                .register(new RegisterRequest("learner", "test-only-password")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verifyNoInteractions(encoder);
    }

    @Test
    void requestToStringDoesNotIncludeCredentials() {
        String password = "test-only-password";
        assertThat(new RegisterRequest("learner", password).toString()).doesNotContain(password);
    }
}
