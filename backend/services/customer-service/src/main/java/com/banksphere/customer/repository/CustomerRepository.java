package com.banksphere.customer.repository;

import com.banksphere.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Customer> findByEmailIgnoreCase(String email);

    /** Phase 9D — OTP login/step-up accepts either identifier; see OtpServiceImpl#findEligibleCustomer. */
    Optional<Customer> findByPhone(String phone);
}
