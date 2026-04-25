package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.mapper.WmsStorageZonesMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageZonesService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

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
        Page<WmsStorageZones> page = PageHelper.startPage(pageNo, pageSize);
        List<WmsStorageZones> list = baseMapper.queryList(wmsStorageZones);
        PageDTO<WmsStorageZones> wmsStorageZonesPageDTO  = new PageDTO<>();
        wmsStorageZonesPageDTO.setTotal(page.getTotal());
        wmsStorageZonesPageDTO.setSize(page.getPageSize());
        wmsStorageZonesPageDTO.setCurrent(page.getPageNum());
        wmsStorageZonesPageDTO.setPages(page.getPages());
        wmsStorageZonesPageDTO.setRecords(list);
        return wmsStorageZonesPageDTO;


        //方法二，使用mybatis-plus的lambda写法
        //return null;
    }
}
