package com.banksphere.account.repository;

import com.banksphere.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(UUID customerId);

    /**
     * Used for recipient resolution (Phase 8B) — {@code account_number} has
     * a DB-level unique constraint (see V1__create_accounts_table.sql), so
     * this can never match more than one row regardless of IFSC.
     */
    Optional<Account> findByAccountNumber(String accountNumber);
}
