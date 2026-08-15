export type CustomerStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";

export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  address: string;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  address: string;
}
