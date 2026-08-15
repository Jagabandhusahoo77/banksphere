package com.banksphere.customer.service;

import com.banksphere.customer.dto.CustomerCreateRequest;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.CustomerUpdateRequest;
import com.banksphere.customer.entity.Customer;
import com.banksphere.customer.entity.CustomerStatus;
import com.banksphere.customer.exception.CustomerAccessDeniedException;
import com.banksphere.customer.exception.CustomerNotFoundException;
import com.banksphere.customer.exception.DuplicateEmailException;
import com.banksphere.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new CustomerCreateRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St, Springfield");
    }

    @Test
    void createCustomer_savesAndReturnsCustomer_whenEmailIsUnique() {
        when(customerRepository.existsByEmailIgnoreCase(createRequest.email())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.createCustomer(createRequest);

        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.email()).isEqualTo("jane.doe@example.com");
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void createCustomer_throwsDuplicateEmailException_whenEmailAlreadyExists() {
        when(customerRepository.existsByEmailIgnoreCase(createRequest.email())).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(createRequest))
                .isInstanceOf(DuplicateEmailException.class);

        verify(customerRepository, never()).save(any());
    }

    @Test
    void getCustomer_returnsCustomer_whenFoundAndRequestingOwnProfile() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(id)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+1-555-0100")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .address("123 Main St")
                .status(CustomerStatus.ACTIVE)
                .build();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomer(id, id);

        assertThat(response.id()).isEqualTo(id);
    }

    @Test
    void getCustomer_throwsNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(id, id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getCustomer_throwsAccessDeniedException_whenRequestingAnotherCustomersProfile() {
        UUID ownId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();

        assertThatThrownBy(() -> customerService.getCustomer(otherCustomerId, ownId))
                .isInstanceOf(CustomerAccessDeniedException.class);

        // Ownership is checked before any repository lookup — the requested
        // customer's existence is never confirmed or denied to a non-owner.
        verify(customerRepository, never()).findById(any());
    }

    @Test
    void updateCustomer_updatesFields_whenCustomerExistsAndRequestingOwnProfile() {
        UUID id = UUID.randomUUID();
        Customer existing = Customer.builder()
                .id(id)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+1-555-0100")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .address("123 Main St")
                .status(CustomerStatus.ACTIVE)
                .build();
        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                "Janet", "Doe", "janet.doe@example.com", "+1-555-0199",
                LocalDate.of(1990, 5, 20), "456 Other St", CustomerStatus.SUSPENDED);

        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByEmailIgnoreCase(updateRequest.email())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.updateCustomer(id, updateRequest, id);

        assertThat(response.firstName()).isEqualTo("Janet");
        assertThat(response.status()).isEqualTo(CustomerStatus.SUSPENDED);
        verify(customerRepository, times(1)).save(existing);
    }

    @Test
    void updateCustomer_throwsNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(id, new CustomerUpdateRequest(
                "Janet", "Doe", "janet.doe@example.com", "+1-555-0199",
                LocalDate.of(1990, 5, 20), "456 Other St", CustomerStatus.ACTIVE), id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void updateCustomer_throwsAccessDeniedException_whenRequestingAnotherCustomersProfile() {
        UUID ownId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                "Janet", "Doe", "janet.doe@example.com", "+1-555-0199",
                LocalDate.of(1990, 5, 20), "456 Other St", CustomerStatus.ACTIVE);

        assertThatThrownBy(() -> customerService.updateCustomer(otherCustomerId, updateRequest, ownId))
                .isInstanceOf(CustomerAccessDeniedException.class);

        verify(customerRepository, never()).save(any());
    }

    // ---- Phase 9B: employee-lookup --------------------------------------

    @Test
    void employeeLookup_returnsSlimProfile_withNoOwnershipCheck() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(id).firstName("John").lastName("Smith").email("john.smith@example.com")
                .phone("+1-555-0100").dateOfBirth(LocalDate.of(1985, 3, 12)).address("1 Test St")
                .status(CustomerStatus.ACTIVE).build();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        var response = customerService.employeeLookup(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void employeeLookup_throwsCustomerNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.employeeLookup(id)).isInstanceOf(CustomerNotFoundException.class);
    }

    // ---- Phase 9C: employee-lookup full profile (Customer 360) ---------

    @Test
    void employeeLookupFullProfile_returnsFullProfile_withNoOwnershipCheck() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(id).firstName("John").lastName("Smith").email("john.smith@example.com")
                .phone("+1-555-0100").dateOfBirth(LocalDate.of(1985, 3, 12)).address("1 Test St")
                .status(CustomerStatus.ACTIVE).build();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        var response = customerService.employeeLookupFullProfile(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("john.smith@example.com");
        assertThat(response.phone()).isEqualTo("+1-555-0100");
        assertThat(response.address()).isEqualTo("1 Test St");
    }

    @Test
    void employeeLookupFullProfile_throwsCustomerNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.employeeLookupFullProfile(id)).isInstanceOf(CustomerNotFoundException.class);
    }
}
