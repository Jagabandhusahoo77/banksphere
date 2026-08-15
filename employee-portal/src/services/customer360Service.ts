import { employeeApiClient } from "./apiClient";
import type { Customer360Response } from "@/types/customer360";

export const customer360Service = {
  async getCustomer360(customerId: string): Promise<Customer360Response> {
    const { data } = await employeeApiClient.get<Customer360Response>(
      `/api/v1/employee/customers/${customerId}/360`,
    );
    return data;
  },
};
