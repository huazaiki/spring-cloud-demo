package com.huazaiki.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazaiki.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
}
