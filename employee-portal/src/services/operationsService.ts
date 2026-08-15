import { employeeApiClient } from "./apiClient";
import type {
  CashDepositHistoryEntry,
  CashDepositRequest,
  CashDepositResponse,
  CustomerSearchResponse,
} from "@/types/operations";

/** The "Employee Operations API" — see docs/architecture/employee-operations.md. */
export const operationsService = {
  async customerSearchByAccountNumber(accountNumber: string): Promise<CustomerSearchResponse> {
    const { data } = await employeeApiClient.get<CustomerSearchResponse>("/api/v1/operations/customer-search", {
      params: { accountNumber },
    });
    return data;
  },

  async customerSearchByCustomerId(customerId: string): Promise<CustomerSearchResponse> {
    const { data } = await employeeApiClient.get<CustomerSearchResponse>("/api/v1/operations/customer-search", {
      params: { customerId },
    });
    return data;
  },

  async cashDeposit(request: CashDepositRequest): Promise<CashDepositResponse> {
    const { data } = await employeeApiClient.post<CashDepositResponse>("/api/v1/operations/cash-deposits", request);
    return data;
  },

  async cashDepositHistory(): Promise<CashDepositHistoryEntry[]> {
    const { data } = await employeeApiClient.get<CashDepositHistoryEntry[]>("/api/v1/operations/cash-deposits/history");
    return data;
  },
};
