import { employeeApiClient } from "./apiClient";
import type { EmployeeLoginRequest, EmployeeLoginResponse, EmployeeResponse } from "@/types/employee";

export const employeeAuthService = {
  async login(request: EmployeeLoginRequest): Promise<EmployeeLoginResponse> {
    const { data } = await employeeApiClient.post<EmployeeLoginResponse>("/api/v1/employees/auth/login", request);
    return data;
  },

  async getCurrentEmployee(): Promise<EmployeeResponse> {
    const { data } = await employeeApiClient.get<EmployeeResponse>("/api/v1/employees/me");
    return data;
  },
};
