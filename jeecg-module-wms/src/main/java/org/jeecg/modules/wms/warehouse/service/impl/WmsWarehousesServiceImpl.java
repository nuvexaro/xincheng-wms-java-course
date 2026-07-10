package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.mapper.WmsWarehousesMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsWarehousesService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 仓库表
 * @Author: jeecg-boot
 * @Date: 2026-07-08
 * @Version: V1.0
 */
@Service
@Slf4j
public class WmsWarehousesServiceImpl extends ServiceImpl<WmsWarehousesMapper, WmsWarehouses> implements IWmsWarehousesService {


    @Override
    public void add(WmsWarehouses wmsWarehouses) {
        //添加时校验仓库代码是否已经存在（仓库只能存在一个）
        //select count(*) from wms_warehouses where warehouse_code = ?
        LambdaQueryWrapper<WmsWarehouses> wrapper = new LambdaQueryWrapper<WmsWarehouses>()
                .eq(WmsWarehouses::getWarehouseCode, wmsWarehouses.getWarehouseCode());
        //如果存在 抛异常
        if (this.count(wrapper) > 0) {
            throw new RuntimeException("代码已存在");
        }

        //如果不存在 正常添加 初始状态为创建
        wmsWarehouses.setStatus(WarehouseDictEnum.STATUS_CREATED.getCode());
        save(wmsWarehouses);
    }

    @Override
    public void edit(WmsWarehouses wmsWarehouses) {
        //添加时校验仓库代码是否已经存在（仓库只能存在一个）
        LambdaQueryWrapper<WmsWarehouses> wrapper = new LambdaQueryWrapper<WmsWarehouses>()
                .eq(WmsWarehouses::getWarehouseCode, wmsWarehouses.getWarehouseCode())
                .ne(WmsWarehouses::getId, wmsWarehouses.getId());
        //如果存在 抛异常
        if (this.count(wrapper) > 0) {
            throw new RuntimeException("代码已存在");
        }

        //如果不存在 正常添加 初始状态为创建
        updateById(wmsWarehouses);
    }

    @Override
    public void enable(String id) {
        //校验参数是否为空
        if (StringUtils.isEmpty(id)) {
            throw new JeecgBootException("参数不能为空");
        }
        //校验是否仓库是否存在
        WmsWarehouses wmsWarehouses = getById(id);
        if (wmsWarehouses == null) {
            throw new JeecgBootException("仓库不存在");
        }
        //仓库状态为“创建”或禁用时方可启用
        if (!WarehouseDictEnum.STATUS_CREATED.getCode().equals(wmsWarehouses.getStatus())
                && !WarehouseDictEnum.STATUS_INACTIVE.getCode().equals(wmsWarehouses.getStatus())) {
            throw new JeecgBootException("仓库状态为“创建”或禁用时方可启用");
        }
        wmsWarehouses.setStatus(WarehouseDictEnum.STATUS_ACTIVE.getCode());
        updateById(wmsWarehouses);

    }

    @Override
    public void disable(String id) {
        //校验参数是否为空
        if (StringUtils.isEmpty(id)) {
            throw new JeecgBootException("参数不能为空");
        }
        //校验是否仓库是否存在
        WmsWarehouses wmsWarehouses = getById(id);
        if (wmsWarehouses == null) {
            throw new JeecgBootException("仓库不存在");
        }
        //仓库状态为“启用”时方可禁用
        if (!WarehouseDictEnum.STATUS_ACTIVE.getCode().equals(wmsWarehouses.getStatus())) {
            throw new JeecgBootException("仓库状态为“启用”时方可禁用");
        }
        log.info("禁用");
        wmsWarehouses.setStatus(WarehouseDictEnum.STATUS_INACTIVE.getCode());
        updateById(wmsWarehouses);

    }
}
