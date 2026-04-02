package org.example.maridone.core.employee;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.dto.EmployeeDetailsDto;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.core.filter.EmployeeFilter;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.embeddable.Address;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CoreMapper coreMapper;

    EmployeeService(EmployeeRepository employeeRepository, CoreMapper coreMapper) {
        this.employeeRepository = employeeRepository;
        this.coreMapper = coreMapper;
    }


    @Transactional
    @ExecutionTime
    public EmployeeResponseDto createEmployee(EmployeeRequestDto employeeRequestDto) {
        Employee employee = coreMapper.toEmployee(employeeRequestDto);
        employeeRepository.save(employee);
        return coreMapper.toEmployeeResponse(employee);
    }

    @ExecutionTime
    public Page<EmployeeResponseDto> getAllEmployees(EmployeeFilter employeeFilter, Pageable pageable) {
        Specification<Employee> specs = EmployeeSpecs.hasFilters(employeeFilter);
        Page<Employee> employeePage = employeeRepository.findAll(specs, pageable);
        return employeePage.map(coreMapper::toEmployeeResponse);
    }

    @ExecutionTime
    public EmployeeResponseDto getEmployee(Long id) {

        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        return coreMapper.toEmployeeResponse(emp);
    }

    @ExecutionTime
    public EmployeeDetailsDto getSelfEmployee(Long id) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        return coreMapper.toEmployeeDetailsDto(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateEmployee(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));

        Optional.ofNullable(updated.getFirstName())
                .filter(v -> !v.equals(emp.getFirstName()))
                .ifPresent(emp::setFirstName);

        Optional.ofNullable(updated.getMiddleName())
                .filter(v -> !v.equals(emp.getMiddleName()))
                .ifPresent(emp::setMiddleName);

        Optional.ofNullable(updated.getLastName())
                .filter(v -> !v.equals(emp.getLastName()))
                .ifPresent(emp::setLastName);

        Optional.ofNullable(updated.getBirthDate())
                .filter(v -> !v.equals(emp.getBirthDate()))
                .ifPresent(emp::setBirthDate);

        Optional.ofNullable(updated.getEmail())
                .filter(v -> !v.equals(emp.getEmail()))
                .ifPresent(newEmail -> {
                    emp.setEmail(newEmail);
                    emp.setIsVerified(false);
                });

        Optional.ofNullable(updated.getPhoneNumber())
                .filter(v -> !v.equals(emp.getPhoneNumber()))
                .ifPresent(emp::setPhoneNumber);

        Optional.ofNullable(updated.getAddress()).ifPresent(newAddr -> {
            Address existing = Optional.ofNullable(emp.getAddress()).orElseGet(() -> {
                Address a = new Address();
                emp.setAddress(a);
                return a;
            });

            Optional.ofNullable(newAddr.getPermanentAddress())
                    .filter(v -> !v.equals(existing.getPermanentAddress()))
                    .ifPresent(existing::setPermanentAddress);

            Optional.ofNullable(newAddr.getTemporaryAddress())
                    .filter(v -> !v.equals(existing.getTemporaryAddress()))
                    .ifPresent(existing::setTemporaryAddress);

            Optional.ofNullable(newAddr.getCity())
                    .filter(v -> !v.equals(existing.getCity()))
                    .ifPresent(existing::setCity);

            Optional.ofNullable(newAddr.getState())
                    .filter(v -> !v.equals(existing.getState()))
                    .ifPresent(existing::setState);

            Optional.ofNullable(newAddr.getCountry())
                    .filter(v -> !v.equals(existing.getCountry()))
                    .ifPresent(existing::setCountry);

            Optional.ofNullable(newAddr.getZipCode())
                    .filter(v -> !v.equals(existing.getZipCode()))
                    .ifPresent(existing::setZipCode);
        });

        employeeRepository.save(emp);

        return coreMapper.toEmployeeResponse(emp);
    }



    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateStatus(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));

        emp.setEmploymentStatus(updated.getEmploymentStatus());
        employeeRepository.save(emp);
        return coreMapper.toEmployeeResponse(emp);
    }

}
