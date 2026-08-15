package com.banksphere.customer.repository;

import com.banksphere.customer.entity.CustomerCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerCredentialsRepository extends JpaRepository<CustomerCredentials, UUID> {
}
