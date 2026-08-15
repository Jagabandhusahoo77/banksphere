package com.banksphere.customer.service;

import com.banksphere.customer.dto.AuthResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.CustomerSummary;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * A precomputed hash of an arbitrary, never-used password, checked
     * (and always fails) when no matching account exists — so a login
     * attempt against an unknown email takes roughly the same time as one
     * against a known email with a wrong password. Without this, the
     * unknown-email path would skip BCrypt entirely and return
     * measurably faster, which is a timing side-channel an attacker could
     * use to enumerate registered emails even though the response body
     * itself is identical either way.
     */
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("dummy-password-for-timing-safety-only");

    private final CustomerRepository customerRepository;
    private final CustomerCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .dateOfBirth(request.dateOfBirth())
                .address(request.address())
                .build();
        Customer savedCustomer = customerRepository.save(customer);

        CustomerCredentials credentials = CustomerCredentials.builder()
                .customerId(savedCustomer.getId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .enabled(true)
                .build();
        credentialsRepository.save(credentials);

        return CustomerResponse.from(savedCustomer);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<Customer> customerOpt = customerRepository.findByEmailIgnoreCase(request.email());
        Optional<CustomerCredentials> credentialsOpt = customerOpt
                .flatMap(customer -> credentialsRepository.findById(customer.getId()));

        String hashToCheck = credentialsOpt.map(CustomerCredentials::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        boolean credentialsValid = customerOpt.isPresent()
                && credentialsOpt.isPresent()
                && credentialsOpt.get().isEnabled()
                && passwordMatches;

        if (!credentialsValid) {
            // Never distinguish "no such email" from "wrong password" from
            // "disabled account" in the response — see InvalidCredentialsException.
            throw new InvalidCredentialsException();
        }

        Customer customer = customerOpt.get();
        CustomerCredentials credentials = credentialsOpt.get();
        credentials.setLastLoginAt(Instant.now());
        credentialsRepository.save(credentials);

        return issueAuthResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse issueAccessToken(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        return issueAuthResponse(customer);
    }

    private AuthResponse issueAuthResponse(Customer customer) {
        String token = jwtService.generateToken(customer.getId(), customer.getEmail());
        return AuthResponse.of(token, jwtService.expirationSeconds(), CustomerSummary.from(customer));
    }

    @Override
    public void logout(UUID customerId) {
        // Stateless JWT: there is no server-side session or token to
        // invalidate in this phase. The frontend discards its stored
        // token; the token itself remains cryptographically valid until
        // it naturally expires. See docs/security/authentication.md.
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        return CustomerResponse.from(customer);
    }
}
