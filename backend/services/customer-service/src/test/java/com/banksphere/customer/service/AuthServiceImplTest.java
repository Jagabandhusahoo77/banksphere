package com.banksphere.customer.service;

import com.banksphere.customer.dto.AuthResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.LoginRequest;
import com.banksphere.customer.dto.RegisterRequest;
import com.banksphere.customer.entity.Customer;
import com.banksphere.customer.entity.CustomerCredentials;
import com.banksphere.customer.exception.CustomerNotFoundException;
import com.banksphere.customer.exception.DuplicateEmailException;
import com.banksphere.customer.exception.InvalidCredentialsException;
import com.banksphere.customer.repository.CustomerCredentialsRepository;
import com.banksphere.customer.repository.CustomerRepository;
import com.banksphere.customer.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCredentialsRepository credentialsRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(customerRepository, credentialsRepository, passwordEncoder, jwtService);
        registerRequest = new RegisterRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St", "Password123");
    }

    @Test
    void register_createsCustomerAndCredentials_whenEmailIsUnique() {
        when(customerRepository.existsByEmailIgnoreCase(registerRequest.email())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = authService.register(registerRequest);

        assertThat(response.email()).isEqualTo("jane.doe@example.com");

        ArgumentCaptor<CustomerCredentials> captor = ArgumentCaptor.forClass(CustomerCredentials.class);
        verify(credentialsRepository).save(captor.capture());
        CustomerCredentials savedCredentials = captor.getValue();
        assertThat(savedCredentials.isEnabled()).isTrue();
        assertThat(savedCredentials.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(passwordEncoder.matches("Password123", savedCredentials.getPasswordHash())).isTrue();
    }

    @Test
    void register_neverLeaksPasswordOrHash_inResponseJson() throws Exception {
        when(customerRepository.existsByEmailIgnoreCase(registerRequest.email())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = authService.register(registerRequest);

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(response);
        assertThat(json.toLowerCase()).doesNotContain("password").doesNotContain("hash");
    }

    @Test
    void register_throwsDuplicateEmailException_whenEmailAlreadyExists() {
        when(customerRepository.existsByEmailIgnoreCase(registerRequest.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest)).isInstanceOf(DuplicateEmailException.class);

        verify(customerRepository, never()).save(any());
        verify(credentialsRepository, never()).save(any());
    }

    @Test
    void login_returnsAccessToken_whenCredentialsAreValid() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId).firstName("Jane").lastName("Doe").email("jane.doe@example.com")
                .phone("+1-555-0100").dateOfBirth(LocalDate.of(1990, 5, 20)).address("123 Main St")
                .build();
        CustomerCredentials credentials = CustomerCredentials.builder()
                .customerId(customerId)
                .passwordHash(passwordEncoder.encode("Password123"))
                .enabled(true)
                .build();

        when(customerRepository.findByEmailIgnoreCase("jane.doe@example.com")).thenReturn(Optional.of(customer));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(credentials));
        when(jwtService.generateToken(customerId, "jane.doe@example.com")).thenReturn("signed.jwt.token");
        when(jwtService.expirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(new LoginRequest("jane.doe@example.com", "Password123"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.customer().email()).isEqualTo("jane.doe@example.com");
        verify(credentialsRepository).save(credentials);
    }

    @Test
    void login_throwsInvalidCredentialsException_whenEmailDoesNotExist() {
        when(customerRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsInvalidCredentialsException_whenPasswordIsWrong() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder().id(customerId).email("jane.doe@example.com").build();
        CustomerCredentials credentials = CustomerCredentials.builder()
                .customerId(customerId)
                .passwordHash(passwordEncoder.encode("Password123"))
                .enabled(true)
                .build();

        when(customerRepository.findByEmailIgnoreCase("jane.doe@example.com")).thenReturn(Optional.of(customer));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(credentials));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane.doe@example.com", "WrongPassword1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsInvalidCredentialsException_whenAccountIsDisabled() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder().id(customerId).email("jane.doe@example.com").build();
        CustomerCredentials credentials = CustomerCredentials.builder()
                .customerId(customerId)
                .passwordHash(passwordEncoder.encode("Password123"))
                .enabled(false)
                .build();

        when(customerRepository.findByEmailIgnoreCase("jane.doe@example.com")).thenReturn(Optional.of(customer));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(credentials));

        // Same generic message as "wrong password" / "unknown email" —
        // deliberately never reveals that the account exists but is disabled.
        assertThatThrownBy(() -> authService.login(new LoginRequest("jane.doe@example.com", "Password123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    /**
     * Phase 9D — the shared tail-end both OTP-login verification and
     * token refresh call once identity is already established some other
     * way (a verified OTP, a rotated refresh token) — it must never
     * re-check a password, only look the customer up and issue a token.
     */
    @Test
    void issueAccessToken_issuesTokenForCustomer_withoutCheckingAnyCredential() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId).firstName("Jane").lastName("Doe").email("jane.doe@example.com")
                .phone("+1-555-0100").dateOfBirth(LocalDate.of(1990, 5, 20)).address("123 Main St")
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(jwtService.generateToken(customerId, "jane.doe@example.com")).thenReturn("signed.jwt.token");
        when(jwtService.expirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.issueAccessToken(customerId);

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.customer().email()).isEqualTo("jane.doe@example.com");
        verify(credentialsRepository, never()).findById(any());
    }

    @Test
    void issueAccessToken_throwsCustomerNotFoundException_forAnUnknownCustomerId() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.issueAccessToken(customerId))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
