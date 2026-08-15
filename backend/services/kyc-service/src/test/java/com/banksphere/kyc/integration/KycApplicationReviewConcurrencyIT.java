package com.banksphere.kyc.integration;

import com.banksphere.kyc.entity.KycApplication;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.repository.KycApplicationRepository;
import com.banksphere.kyc.security.EmployeePrincipal;
import com.banksphere.kyc.service.KycApplicationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "lost update" concurrency scenario from this phase's own
 * instructions: Officer A opens an application, Officer B opens the same
 * application, Officer A approves, Officer B attempts to reject — Officer
 * B must receive a conflict, never silently overwrite Officer A's
 * decision. Same real-Postgres-required pattern as account-service's
 * {@code AccountTransferConcurrencyIT} (see ADR-004) — {@code
 * KycApplication.version} (a plain {@code @Version Long}, identical
 * mechanism to {@code Account.version}) is what makes this safe. See
 * ADR-008.
 *
 * <p>As with the account-service precedent, which side loses is not
 * asserted deterministically — {@link ObjectOptimisticLockingFailureException}
 * is thrown by whichever transaction's flush loses the race. What IS
 * asserted is the invariant: exactly one decision wins, and the
 * application's final state is one of the two decisions, never a
 * corrupted mix.
 */
@SpringBootTest
class KycApplicationReviewConcurrencyIT {

    private static final Logger log = LoggerFactory.getLogger(KycApplicationReviewConcurrencyIT.class);

    @Autowired
    private KycApplicationService kycApplicationService;

    @Autowired
    private KycApplicationRepository kycApplicationRepository;

    @BeforeAll
    static void checkPostgresReachable() {
        PostgresAssumptions.assumeReachable();
    }

    @Test
    void concurrentApproveAndReject_allowsOnlyOneDecisionToWin_theOtherReceivesAConflict() throws Exception {
        UUID applicationId = seedUnderReviewApplication();
        EmployeePrincipal officerA = employee();
        EmployeePrincipal officerB = employee();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        Callable<String> approveAttempt = () -> attemptApprove(startBarrier, officerA, applicationId);
        Callable<String> rejectAttempt = () -> attemptReject(startBarrier, officerB, applicationId);

        try {
            Future<String> future1 = executor.submit(approveAttempt);
            Future<String> future2 = executor.submit(rejectAttempt);

            String outcome1 = future1.get(15, TimeUnit.SECONDS);
            String outcome2 = future2.get(15, TimeUnit.SECONDS);

            List<String> outcomes = List.of(outcome1, outcome2);
            log.info("Concurrent review outcomes: {}", outcomes);

            long successes = outcomes.stream().filter(o -> o.equals("APPROVE_SUCCESS") || o.equals("REJECT_SUCCESS")).count();
            long conflicts = outcomes.stream().filter("OPTIMISTIC_LOCK_CONFLICT"::equals).count();

            assertThat(successes)
                    .as("exactly one of the two concurrent decisions must win")
                    .isEqualTo(1);
            assertThat(conflicts)
                    .as("the losing decision must fail safely with a conflict, never silently overwrite the winner")
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        KycApplication reloaded = kycApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("final status must be exactly one of the two attempted decisions, never a corrupted mix")
                .isIn(KycStatus.APPROVED, KycStatus.REJECTED);
    }

    private String attemptApprove(CyclicBarrier startBarrier, EmployeePrincipal employee, UUID applicationId) {
        try {
            startBarrier.await(10, TimeUnit.SECONDS);
            kycApplicationService.approve(employee, applicationId);
            return "APPROVE_SUCCESS";
        } catch (ObjectOptimisticLockingFailureException ex) {
            return "OPTIMISTIC_LOCK_CONFLICT";
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected approve failure mode: " + ex, ex);
        }
    }

    private String attemptReject(CyclicBarrier startBarrier, EmployeePrincipal employee, UUID applicationId) {
        try {
            startBarrier.await(10, TimeUnit.SECONDS);
            kycApplicationService.reject(employee, applicationId, "concurrency-it rejection");
            return "REJECT_SUCCESS";
        } catch (ObjectOptimisticLockingFailureException ex) {
            return "OPTIMISTIC_LOCK_CONFLICT";
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected reject failure mode: " + ex, ex);
        }
    }

    private EmployeePrincipal employee() {
        return new EmployeePrincipal(UUID.randomUUID(), "EMP" + System.nanoTime() % 100000, UUID.randomUUID(),
                "BANK0HQ0001", List.of("KYC_OFFICER"), List.of("KYC_VIEW", "KYC_REVIEW", "KYC_APPROVE", "KYC_REJECT"));
    }

    private UUID seedUnderReviewApplication() {
        UUID id = UUID.randomUUID();
        kycApplicationRepository.saveAndFlush(KycApplication.builder()
                .id(id)
                .customerId(UUID.randomUUID())
                .status(KycStatus.UNDER_REVIEW)
                .panNumber("ABCDE1234F")
                .occupation("Engineer")
                .annualIncomeRange("5-10L")
                .version(0L)
                .build());
        return id;
    }
}
