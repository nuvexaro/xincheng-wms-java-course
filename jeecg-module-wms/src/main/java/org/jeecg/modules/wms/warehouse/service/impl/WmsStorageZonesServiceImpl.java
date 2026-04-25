package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.github.pagehelper.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.mapper.WmsStorageZonesMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageZonesService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jeecg.modules.wms.config.WarehouseDictEnum.*;

/**
 * @Description: 储区表
 * @Author: jeecg-boot
 * @Date:   2026-04-23
 * @Version: V1.0
 */
@Service
public class WmsStorageZonesServiceImpl extends ServiceImpl<WmsStorageZonesMapper, WmsStorageZones> implements IWmsStorageZonesService {

    @Override
    public IPage<WmsStorageZones> queryPageList(WmsStorageZones wmsStorageZones, Integer pageNo, Integer pageSize) {
        //方法一，使用传统的sql写法
//        Page<WmsStorageZones> page = PageHelper.startPage(pageNo, pageSize);
//        List<WmsStorageZones> list = baseMapper.queryList(wmsStorageZones);
//        PageDTO<WmsStorageZones> wmsStorageZonesPageDTO  = new PageDTO<>();
//        wmsStorageZonesPageDTO.setTotal(page.getTotal());
//        wmsStorageZonesPageDTO.setSize(page.getPageSize());
//        wmsStorageZonesPageDTO.setCurrent(page.getPageNum());
//        wmsStorageZonesPageDTO.setPages(page.getPages());
//        wmsStorageZonesPageDTO.setRecords(list);
//        return wmsStorageZonesPageDTO;


        //方法二，使用mybatis-plus的lambda写法

        Page<WmsStorageZones> page = new Page<>(pageNo, pageSize);
//		IPage<WmsStorageZones> pageList = wmsStorageZonesService.page(page, queryWrapper);
        Page<WmsStorageZones> pageList = lambdaQuery()
                .like(StringUtils.isNotEmpty(wmsStorageZones.getZoneCode()), WmsStorageZones::getZoneCode, wmsStorageZones.getZoneCode())
                .eq(StringUtils.isNotEmpty(wmsStorageZones.getStatus()),WmsStorageZones::getStatus,wmsStorageZones.getStatus())
                .eq(wmsStorageZones.getWarehouseId()!=null,WmsStorageZones::getWarehouseId,wmsStorageZones.getWarehouseId())
                .page(page);

        //获取仓库ids
        List<String> warehouseIds = pageList.getRecords().stream().map(WmsStorageZones::getWarehouseId).collect(Collectors.toList());

        List<WmsWarehouses> wmsWarehousesList = Db.lambdaQuery(WmsWarehouses.class)
                .select(WmsWarehouses::getId, WmsWarehouses::getWarehouseName)
                .in(CollectionUtils.isNotEmpty(warehouseIds), WmsWarehouses::getId, warehouseIds)
                .list();

        Map<String, List<WmsWarehouses>> warehouseMap = wmsWarehousesList.stream().collect(Collectors.groupingBy(WmsWarehouses::getId));

        pageList.getRecords().forEach(item -> {
            item.setWarehouseName(warehouseMap.get(item.getWarehouseId()).get(0).getWarehouseName());
        });


        return pageList;
    }

    @Override
    public void enable(String id) {
        if(StringUtils.isEmpty(id)){
            throw new JeecgBootException("id不能为空");
        }

        WmsStorageZones wmsStorageZones = this.getById(id);
        if(wmsStorageZones == null){
            throw new JeecgBootException("储区不存在");
        }

        if(!(STATUS_CREATED.getCode().equals(wmsStorageZones.getStatus()) || STATUS_INACTIVE.getCode().equals(wmsStorageZones.getStatus()))){
            throw new JeecgBootException("储区状态不为创建或者禁用，无法启用");
        }

        lambdaUpdate()
                .set(WmsStorageZones::getStatus,STATUS_ACTIVE.getCode())
                .eq(WmsStorageZones::getId,id)
                .update();

    }

    @Override
    public void disable(String id) {
        if(StringUtils.isEmpty(id)){
            throw new JeecgBootException("id不能为空");
        }

        WmsStorageZones wmsStorageZones = this.getById(id);
        if(wmsStorageZones == null){
            throw new JeecgBootException("储区不存在");
        }

        if(!(STATUS_ACTIVE.getCode().equals(wmsStorageZones.getStatus()))){
            throw new JeecgBootException("储区状态不为创建或者禁用，无法启用");
        }

        lambdaUpdate()
                .set(WmsStorageZones::getStatus,STATUS_INACTIVE.getCode())
                .eq(WmsStorageZones::getId,id)
                .update();
    }
}
