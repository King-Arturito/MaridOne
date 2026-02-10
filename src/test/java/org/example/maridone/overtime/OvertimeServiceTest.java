package org.example.maridone.overtime;

import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.Status;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OvertimeServiceTest {

    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OvertimeMapper overtimeMapper;

    @InjectMocks
    private OvertimeService overtimeService;

    @Test
    void createOvertimeRequest_ShouldSave() {
        String username = "niko123";
        OvertimeRequestDto dto = new OvertimeRequestDto();
        OvertimeRequest request = new OvertimeRequest();
        Employee emp = new Employee();

        when(overtimeMapper.toOvertimeRequest(dto)).thenReturn(request);
        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);

        OvertimeRequest result = overtimeService.createOvertimeRequest(dto, username);
        
        Assertions.assertNotNull(result);
    }

    @Test
    void updateOvertimeRequest_ShouldSucceed_WhenPending() {
        Long otId = 1L;
        String approver = "admin";
        OvertimeUpdateDto updateDto = new OvertimeUpdateDto();
        updateDto.setOvertimeId(otId);
        updateDto.setUpdateStatus(Status.APPROVED);

        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", otId); 
        request.setRequestStatus(Status.PENDING); 

        Employee admin = new Employee();
        admin.setFirstName("Admin");
        admin.setLastName("User");

        when(overtimeRequestRepository.findById(otId)).thenReturn(Optional.of(request));
        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(admin));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        
        OvertimeResponseDto responseDto = new OvertimeResponseDto();
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(responseDto);

        OvertimeResponseDto result = overtimeService.updateOvertimeRequest(updateDto, approver);
        
        Assertions.assertNotNull(result);
    }
}