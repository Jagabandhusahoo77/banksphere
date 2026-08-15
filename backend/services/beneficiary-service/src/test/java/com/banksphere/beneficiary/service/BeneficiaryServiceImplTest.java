package com.banksphere.beneficiary.service;

import com.banksphere.beneficiary.dto.BeneficiaryResponse;
import com.banksphere.beneficiary.dto.CreateBeneficiaryRequest;
import com.banksphere.beneficiary.dto.UpdateBeneficiaryRequest;
import com.banksphere.beneficiary.entity.Beneficiary;
import com.banksphere.beneficiary.entity.BeneficiaryStatus;
import com.banksphere.beneficiary.exception.BeneficiaryAccessDeniedException;
import com.banksphere.beneficiary.exception.BeneficiaryNotFoundException;
import com.banksphere.beneficiary.exception.DuplicateBeneficiaryException;
import com.banksphere.beneficiary.repository.BeneficiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceImplTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    private BeneficiaryServiceImpl beneficiaryService;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        beneficiaryService = new BeneficiaryServiceImpl(beneficiaryRepository);
        customerId = UUID.randomUUID();
    }

    private Beneficiary activeBeneficiary(UUID owner) {
        return Beneficiary.builder()
                .id(UUID.randomUUID())
                .customerId(owner)
                .beneficiaryName("John Doe")
                .accountNumber("123456789012")
                .ifsc("BANK0001234")
                .bankName("Example Bank")
                .nickname("John")
                .status(BeneficiaryStatus.ACTIVE)
                .build();
    }

    private CreateBeneficiaryRequest createRequest() {
        return new CreateBeneficiaryRequest("John Doe", "123456789012", "BANK0001234", "Example Bank", "John");
    }

    @Test
    void createBeneficiary_persistsBeneficiaryOwnedByAuthenticatedCaller_whenNoDuplicateExists() {
        when(beneficiaryRepository.existsByCustomerIdAndAccountNumberAndIfscAndStatus(any(), any(), any(), any())).thenReturn(false);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenAnswer(inv -> inv.getArgument(0));

        BeneficiaryResponse response = beneficiaryService.createBeneficiary(createRequest(), customerId);

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.beneficiaryName()).isEqualTo("John Doe");
        assertThat(response.status()).isEqualTo(BeneficiaryStatus.ACTIVE);

        ArgumentCaptor<Beneficiary> captor = ArgumentCaptor.forClass(Beneficiary.class);
        verify(beneficiaryRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void createBeneficiary_throwsDuplicateBeneficiaryException_whenActiveDuplicateAlreadyExists() {
        when(beneficiaryRepository.existsByCustomerIdAndAccountNumberAndIfscAndStatus(
                customerId, "123456789012", "BANK0001234", BeneficiaryStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> beneficiaryService.createBeneficiary(createRequest(), customerId))
                .isInstanceOf(DuplicateBeneficiaryException.class);

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    void getBeneficiaries_returnsOnlyActiveBeneficiariesForRequestingCustomer() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        when(beneficiaryRepository.findByCustomerIdAndStatus(customerId, BeneficiaryStatus.ACTIVE)).thenReturn(List.of(beneficiary));

        List<BeneficiaryResponse> responses = beneficiaryService.getBeneficiaries(customerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).customerId()).isEqualTo(customerId);
    }

    @Test
    void getBeneficiary_returnsBeneficiary_whenOwnedByRequestingCustomer() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        BeneficiaryResponse response = beneficiaryService.getBeneficiary(beneficiary.getId(), customerId);

        assertThat(response.id()).isEqualTo(beneficiary.getId());
    }

    @Test
    void getBeneficiary_throwsNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(beneficiaryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> beneficiaryService.getBeneficiary(id, customerId))
                .isInstanceOf(BeneficiaryNotFoundException.class);
    }

    @Test
    void getBeneficiary_throwsAccessDeniedException_whenRequestedByNonOwner() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        UUID someoneElse = UUID.randomUUID();
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        assertThatThrownBy(() -> beneficiaryService.getBeneficiary(beneficiary.getId(), someoneElse))
                .isInstanceOf(BeneficiaryAccessDeniedException.class);
    }

    @Test
    void updateBeneficiary_updatesDisplayFields_whenOwnedByRequestingCustomer() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBeneficiaryRequest request = new UpdateBeneficiaryRequest("Johnny Doe", "New Bank Name", "Johnny");
        BeneficiaryResponse response = beneficiaryService.updateBeneficiary(beneficiary.getId(), request, customerId);

        assertThat(response.beneficiaryName()).isEqualTo("Johnny Doe");
        assertThat(response.bankName()).isEqualTo("New Bank Name");
        assertThat(response.nickname()).isEqualTo("Johnny");
        // account number / IFSC are never touched by an update — see UpdateBeneficiaryRequest's doc comment.
        assertThat(response.accountNumber()).isEqualTo("123456789012");
        assertThat(response.ifsc()).isEqualTo("BANK0001234");
    }

    @Test
    void updateBeneficiary_throwsAccessDeniedException_whenNotOwnedByRequestingCustomer() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        UUID someoneElse = UUID.randomUUID();
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        UpdateBeneficiaryRequest request = new UpdateBeneficiaryRequest("Johnny Doe", "New Bank Name", "Johnny");

        assertThatThrownBy(() -> beneficiaryService.updateBeneficiary(beneficiary.getId(), request, someoneElse))
                .isInstanceOf(BeneficiaryAccessDeniedException.class);

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    void deactivateBeneficiary_setsStatusToInactive_whenOwnedByRequestingCustomer() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenAnswer(inv -> inv.getArgument(0));

        beneficiaryService.deactivateBeneficiary(beneficiary.getId(), customerId);

        ArgumentCaptor<Beneficiary> captor = ArgumentCaptor.forClass(Beneficiary.class);
        verify(beneficiaryRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BeneficiaryStatus.INACTIVE);
        // the row is never removed — see BeneficiaryService.deactivateBeneficiary's doc comment.
        verify(beneficiaryRepository, never()).delete(any());
        verify(beneficiaryRepository, never()).deleteById(any());
    }

    @Test
    void deactivateBeneficiary_throwsAccessDeniedException_whenNotOwnedByRequestingCustomer() {
        Beneficiary beneficiary = activeBeneficiary(customerId);
        UUID someoneElse = UUID.randomUUID();
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        assertThatThrownBy(() -> beneficiaryService.deactivateBeneficiary(beneficiary.getId(), someoneElse))
                .isInstanceOf(BeneficiaryAccessDeniedException.class);

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    void deactivateBeneficiary_throwsNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(beneficiaryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> beneficiaryService.deactivateBeneficiary(id, customerId))
                .isInstanceOf(BeneficiaryNotFoundException.class);
    }

    // ---- Phase 9C: getBeneficiariesForEmployee (Customer 360) -----------

    @Test
    void getBeneficiariesForEmployee_returnsActiveBeneficiaries_forGivenCustomerId() {
        when(beneficiaryRepository.findByCustomerIdAndStatus(customerId, BeneficiaryStatus.ACTIVE))
                .thenReturn(List.of(activeBeneficiary(customerId)));

        List<BeneficiaryResponse> result = beneficiaryService.getBeneficiariesForEmployee(customerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).customerId()).isEqualTo(customerId);
    }
}
