package com.banksphere.customer.service;

import com.banksphere.customer.dto.CustomerCreateRequest;
import com.banksphere.customer.dto.CustomerLookupResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.CustomerUpdateRequest;
import com.banksphere.customer.entity.Customer;
import com.banksphere.customer.exception.CustomerAccessDeniedException;
import com.banksphere.customer.exception.CustomerNotFoundException;
import com.banksphere.customer.exception.DuplicateEmailException;
import com.banksphere.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerCreateRequest request) {
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

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID id, UUID requestingCustomerId) {
        requireOwnership(id, requestingCustomerId);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        return CustomerResponse.from(customer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerUpdateRequest request, UUID requestingCustomerId) {
        requireOwnership(id, requestingCustomerId);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        if (!customer.getEmail().equalsIgnoreCase(request.email())
                && customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setAddress(request.address());
        customer.setStatus(request.status());

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerLookupResponse employeeLookup(UUID id) {
        Customer customer = customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        return CustomerLookupResponse.from(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse employeeLookupFullProfile(UUID id) {
        Customer customer = customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        return CustomerResponse.from(customer);
    }

    /**
     * Checked before the existence lookup, deliberately: if a customer
     * tries to probe another customer's id, they get the same 403 whether
     * or not that id actually exists — never a 404-vs-403 distinction
     * that would confirm/deny another customer's existence.
     */
    private void requireOwnership(UUID requestedId, UUID requestingCustomerId) {
        if (!requestedId.equals(requestingCustomerId)) {
            throw new CustomerAccessDeniedException(requestedId);
        }
    }
}
