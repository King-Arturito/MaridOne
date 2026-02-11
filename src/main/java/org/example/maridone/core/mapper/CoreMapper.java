package org.example.maridone.core.mapper;

import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.dto.BankAccountDto;
import org.example.maridone.core.dto.EmployeeDetailsDto;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.dto.UserAccountDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CoreMapper {

    @Mapping(source = "employeeId", target = "id")
    EmployeeResponseDto toEmployeeResponse(Employee employee);

    @Mapping(source = "employeeId", target = "id")
    EmployeeDetailsDto toEmployeeDetailsDto(Employee employee);

    @Mapping(source = "employeeId", target = "id")
    List<EmployeeResponseDto> toEmployeeResponses(List<Employee> employees);

    Employee toEmployee(EmployeeRequestDto employeeRequestDto);

    BankAccountDto toBankAccountDto(BankAccount bankAccount);

    BankAccount toBankAccount(BankAccountDto bankAccountDto);

    UserAccountDto toUserAccountDto(UserAccount userAccount);

    UserAccount toUserAccount(UserAccountDto userAccountDto);

    List<BankAccountDto> toBankAccountDtos(List<BankAccount> bankAccounts);

}
