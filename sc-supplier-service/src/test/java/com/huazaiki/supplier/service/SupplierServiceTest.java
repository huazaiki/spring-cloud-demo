package com.huazaiki.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huazaiki.supplier.entity.Supplier;
import com.huazaiki.supplier.mapper.SupplierMapper;
import com.huazaiki.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierService")
class SupplierServiceTest {

    @Mock private SupplierMapper supplierMapper;
    @InjectMocks private SupplierService supplierService;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should throw when credit code already exists")
        void shouldThrowWhenCreditCodeExists() {
            when(supplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThrows(BusinessException.class, () ->
                    supplierService.create("Acme", "91110000MA001", "John", "13800001111"));
        }

        @Test
        @DisplayName("should insert supplier with ACTIVE status")
        void shouldInsertWithActiveStatus() {
            when(supplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(supplierMapper.insert(any(Supplier.class))).thenReturn(1);

            supplierService.create("Acme", "91110000MA001", "John", "13800001111");
            verify(supplierMapper).insert(argThat((Supplier s) -> "ACTIVE".equals(s.getStatus())));
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("should throw when supplier not found")
        void shouldThrowWhenNotFound() {
            when(supplierMapper.selectById(99L)).thenReturn(null);
            assertThrows(BusinessException.class, () ->
                    supplierService.updateStatus(99L, "DISQUALIFIED"));
        }
    }
}
