package com.banksphere.account.dto;

import com.banksphere.account.entity.Account;

/**
 * Deliberately minimal (ADR-005): no internal account id, no customerId,
 * no balance — just enough for the transfer UI to show a recipient
 * preview and for the frontend to know the account number/IFSC it
 * already has are valid. {@code bankName} is always the constant
 * "BankSphere" today (see {@code AccountServiceImpl#BANKSPHERE_IFSC}) —
 * included so the frontend never has to hardcode it, and so this
 * response shape doesn't need to change if BankSphere ever adds real
 * branches with their own names.
 */
public record ResolveRecipientResponse(
        String accountNumber,
        String ifsc,
        String bankName
) {
    public static ResolveRecipientResponse of(Account account) {
        return new ResolveRecipientResponse(account.getAccountNumber(), account.getIfsc(), "BankSphere");
    }
}
