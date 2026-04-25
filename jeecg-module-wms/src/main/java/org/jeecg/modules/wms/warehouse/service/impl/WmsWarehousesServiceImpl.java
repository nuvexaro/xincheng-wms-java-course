package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.zhyd.oauth.utils.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.mapper.WmsWarehousesMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsWarehousesService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import static org.jeecg.modules.wms.config.WarehouseDictEnum.STATUS_CREATED;

/**
 * @Description: 仓库表
 * @Author: jeecg-boot
 * @Date:   2026-04-23
 * @Version: V1.0
 */
@Service
public class WmsWarehousesServiceImpl extends ServiceImpl<WmsWarehousesMapper, WmsWarehouses> implements IWmsWarehousesService {

    @Override
    public void add(WmsWarehouses wmsWarehouses) {
        //添加时校验仓库代码是否已存在
        LambdaQueryWrapper<WmsWarehouses> queryWrapper =
                    new LambdaQueryWrapper<WmsWarehouses>()
                        .eq(WmsWarehouses::getWarehouseCode, wmsWarehouses.getWarehouseCode())
                ;

        if (this.count(queryWrapper) > 0) {
            throw new JeecgBootException(String.format("仓库代码 %s 已存在", wmsWarehouses.getWarehouseCode()));
        }
        //初始状态为“创建”
        wmsWarehouses.setStatus(WarehouseDictEnum.STATUS_CREATED.getCode());

        this.save(wmsWarehouses);


    }

    @Override
    public void edit(WmsWarehouses wmsWarehouses) {
        //编辑时校验仓库代码是否已存在
        LambdaQueryWrapper<WmsWarehouses> queryWrapper =
                new LambdaQueryWrapper<WmsWarehouses>()
                        .eq(WmsWarehouses::getWarehouseCode, wmsWarehouses.getWarehouseCode())
                        .ne(WmsWarehouses::getId, wmsWarehouses.getId())
                ;

        if (this.count(queryWrapper) > 0) {
            throw new JeecgBootException(String.format("仓库代码 %s 已存在", wmsWarehouses.getWarehouseCode()));
        }

        this.updateById(wmsWarehouses);

    }

    @Override
    public void enable(String id) {
        if(StringUtils.isEmpty(id)){
            throw new JeecgBootException("仓库ID不能为空");
        }
        WmsWarehouses wmsWarehouse = this.getById(id);
        if(wmsWarehouse == null){
            throw new JeecgBootException("仓库不存在");
        }

        //仓库状态为“创建”或禁用时方可启用
        if(!(WarehouseDictEnum.STATUS_CREATED.getCode().equals(wmsWarehouse.getStatus()) || WarehouseDictEnum.STATUS_INACTIVE.getCode().equals(wmsWarehouse.getStatus()))){
            throw new JeecgBootException("仓库状态不为创建或者禁用，无法启用");
        }
        wmsWarehouse.setStatus(WarehouseDictEnum.STATUS_ACTIVE.getCode());
        this.updateById(wmsWarehouse);
    }

    @Override
    public void disable(String id) {
        if(StringUtils.isEmpty(id)){
            throw new JeecgBootException("仓库ID不能为空");
        }
        WmsWarehouses wmsWarehouse = this.getById(id);
        if(wmsWarehouse == null){
            throw new JeecgBootException("仓库不存在");
        }

        //仓库状态为“启用”时方可禁用
        if(!(WarehouseDictEnum.STATUS_ACTIVE.getCode().equals(wmsWarehouse.getStatus()) )){
            throw new JeecgBootException("仓库状态不为创建或者禁用，无法启用");
        }
        wmsWarehouse.setStatus(WarehouseDictEnum.STATUS_INACTIVE.getCode());
        this.updateById(wmsWarehouse);
    }
}
