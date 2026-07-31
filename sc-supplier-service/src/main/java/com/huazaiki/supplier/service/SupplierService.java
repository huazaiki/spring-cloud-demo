package com.huazaiki.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.supplier.entity.Supplier;
import com.huazaiki.supplier.mapper.SupplierMapper;
import org.springframework.stereotype.Service;

@Service
public class SupplierService {

    private final SupplierMapper supplierMapper;

    public SupplierService(SupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    public void create(String name, String creditCode, String contactName, String contactPhone) {
        if (supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCreditCode, creditCode)) > 0) {
            throw new BusinessException(() -> 400, "Credit code already exists: " + creditCode);
        }

        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setCreditCode(creditCode);
        supplier.setContactName(contactName);
        supplier.setContactPhone(contactPhone);
        supplier.setStatus("ACTIVE");
        supplierMapper.insert(supplier);
    }

    public Supplier getById(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException(() -> 404, "Supplier not found: " + id);
        }
        return supplier;
    }

    public Page<Supplier> list(int page, int size, String name) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(Supplier::getName, name);
        }
        return supplierMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public void updateStatus(Long id, String status) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException(() -> 404, "Supplier not found: " + id);
        }
        supplier.setStatus(status);
        supplierMapper.updateById(supplier);
    }
}
