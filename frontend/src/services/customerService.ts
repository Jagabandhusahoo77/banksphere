import { customerApiClient } from "./apiClient";
import type { Customer, CustomerCreateRequest } from "@/types/customer";

export const customerService = {
  async getCustomer(id: string): Promise<Customer> {
    const { data } = await customerApiClient.get<Customer>(`/api/v1/customers/${id}`);
    return data;
  },

  async createCustomer(request: CustomerCreateRequest): Promise<Customer> {
    const { data } = await customerApiClient.post<Customer>("/api/v1/customers", request);
    return data;
  },
};
