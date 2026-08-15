export interface BranchSummary {
  id: string;
  branchCode: string;
  branchName: string;
  ifsc: string;
}

export type EmployeeStatus = "ACTIVE" | "INACTIVE" | "LOCKED";

export interface EmployeeSummary {
  id: string;
  employeeNumber: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  status: EmployeeStatus;
}

export interface EmployeeResponse {
  id: string;
  employeeNumber: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  roles: string[];
  permissions: string[];
  branch: BranchSummary;
  status: EmployeeStatus;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeLoginRequest {
  username: string;
  password: string;
}

export interface EmployeeLoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  employee: EmployeeSummary;
  roles: string[];
  permissions: string[];
  branch: BranchSummary;
}

/**
 * The shape actually kept in the auth context/storage — a flattened
 * combination of an EmployeeLoginResponse's pieces, so every screen has a
 * single object to read from rather than reaching into nested
 * employee/roles/permissions/branch fields separately. Never carries
 * anything password-related — there is no such field anywhere upstream of
 * this type (see backend EmployeeResponse's own javadoc).
 */
export interface AuthenticatedEmployee {
  id: string;
  employeeNumber: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  status: EmployeeStatus;
  roles: string[];
  permissions: string[];
  branch: BranchSummary;
}
