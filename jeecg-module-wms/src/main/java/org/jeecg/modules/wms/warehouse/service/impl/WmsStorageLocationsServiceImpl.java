package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.mapper.WmsStorageLocationsMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageLocationsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jeecg.modules.wms.config.WarehouseDictEnum.*;

/**
 * @Description: 储位表
 * @Author: jeecg-boot
 * @Date:   2026-04-23
 * @Version: V1.0
 */
@Service
public class WmsStorageLocationsServiceImpl extends ServiceImpl<WmsStorageLocationsMapper, WmsStorageLocations> implements IWmsStorageLocationsService {

    @Override
    public IPage<WmsStorageLocations> queryPageList(WmsStorageLocations wmsStorageLocations, Integer pageNo, Integer pageSize) {

        Page<WmsStorageLocations> page = new Page<>(pageNo, pageSize);
        IPage<WmsStorageLocations> pageList = lambdaQuery()
                .like(StringUtils.isNotEmpty(wmsStorageLocations.getLocationCode()), WmsStorageLocations::getLocationCode, wmsStorageLocations.getLocationCode())
                .eq(StringUtils.isNotEmpty(wmsStorageLocations.getStatus()),WmsStorageLocations::getStatus,wmsStorageLocations.getStatus())
                .eq(wmsStorageLocations.getWarehouseId()!=null,WmsStorageLocations::getWarehouseId,wmsStorageLocations.getWarehouseId())
                .eq(wmsStorageLocations.getZoneId()!=null,WmsStorageLocations::getZoneId,wmsStorageLocations.getZoneId())
                .page(page);

        //获取仓库ids 并转化为id2Map的形式
        List<String> warehouseIds = pageList.getRecords().stream().map(WmsStorageLocations::getWarehouseId).collect(Collectors.toList());

        Map<String, String> warehouseId2NameMap = Db.lambdaQuery(WmsWarehouses.class)
                .select(WmsWarehouses::getId, WmsWarehouses::getWarehouseName)
                .in(CollectionUtils.isNotEmpty(warehouseIds), WmsWarehouses::getId, warehouseIds)
                .list()
                .stream().collect(Collectors.toMap(WmsWarehouses::getId, WmsWarehouses::getWarehouseName));

        //获取储区ids 并转化为id2Map的形式
        List<String> zoneIds = pageList.getRecords().stream().map(WmsStorageLocations::getZoneId).collect(Collectors.toList());

        Map<String, String> zoneId2NameMap = Db.lambdaQuery(WmsStorageZones.class)
                .select(WmsStorageZones::getId, WmsStorageZones::getZoneName)
                .in(CollectionUtils.isNotEmpty(zoneIds), WmsStorageZones::getId, zoneIds)
                .list()
                .stream().collect(Collectors.toMap(WmsStorageZones::getId, WmsStorageZones::getZoneName));

        pageList.getRecords().forEach(item -> {
            item.setWarehouseName(warehouseId2NameMap.get(item.getWarehouseId()));
            item.setZoneName(zoneId2NameMap.get(item.getZoneId()));
        });

        return pageList;
    }

    @Override
    public void enable(String id) {

        if(StringUtils.isEmpty(id)){
            throw new JeecgBootException("储位ID不能为空");
        }

        WmsStorageLocations wmsStorageLocations = this.getById(id);
        if(wmsStorageLocations == null){
            throw new JeecgBootException("储位不存在");
        }

        if(!(STATUS_CREATED.getCode().equals(wmsStorageLocations.getStatus()) || STATUS_INACTIVE.getCode().equals(wmsStorageLocations.getStatus()))){
            throw new JeecgBootException("储位状态不为创建或者禁用，无法启用");
        }

        wmsStorageLocations.setStatus(STATUS_ACTIVE.getCode());
        this.updateById(wmsStorageLocations);
    }

    @Override
    public void disable(String id) {
        if(StringUtils.isEmpty(id)){
            throw new JeecgBootException("储位ID不能为空");
        }

        WmsStorageLocations wmsStorageLocations = this.getById(id);
        if(wmsStorageLocations == null){
            throw new JeecgBootException("储位不存在");
        }

        if(!(STATUS_ACTIVE.getCode().equals(wmsStorageLocations.getStatus()) )) {
            throw new JeecgBootException("储位状态不为启用，无法禁用");
        }

        lambdaUpdate()
                .set(WmsStorageLocations::getStatus,STATUS_INACTIVE.getCode())
                .eq(WmsStorageLocations::getId,id)
                .update();
    }
}
