package com.huazaiki.auth.controller;

import com.huazaiki.auth.entity.SysDept;
import com.huazaiki.auth.mapper.SysDeptMapper;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/depts")
public class DeptController {

    private final SysDeptMapper deptMapper;

    public DeptController(SysDeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    @RequirePermission("dept:manage")
    @GetMapping
    public ApiResponse<List<SysDept>> list() {
        return ApiResponse.success(deptMapper.selectList(null));
    }

    @RequirePermission("dept:manage")
    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, String> body) {
        SysDept dept = new SysDept();
        dept.setDeptCode(body.get("deptCode"));
        dept.setDeptName(body.get("deptName"));
        dept.setParentId(body.get("parentId") != null ? Long.valueOf(body.get("parentId")) : 0L);
        dept.setSortNo(0);
        dept.setStatus("ACTIVE");
        deptMapper.insert(dept);
        return ApiResponse.success();
    }
}