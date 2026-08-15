package com.banksphere.employee.repository;

import com.banksphere.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUsername(String username);

    boolean existsByEmployeeNumber(String employeeNumber);

    boolean existsByUsername(String username);
}
