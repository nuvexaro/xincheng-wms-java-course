package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import org.jeecg.modules.wms.warehouse.mapper.WmsStorageLocationsMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageLocationsService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

/**
 * @Description: 储位表
 * @Author: jeecg-boot
 * @Date: 2026-07-08
 * @Version: V1.0
 */
@Service
public class WmsStorageLocationsServiceImpl extends ServiceImpl<WmsStorageLocationsMapper, WmsStorageLocations> implements IWmsStorageLocationsService {

    /**
     * 储位启用
     *
     * @param id
     */
    @Override
    public void enable(String id) {
        //校验参数是否为空
        if (StringUtils.isEmpty(id)) {
            throw new JeecgBootException("参数不能为空");
        }
        //校验是否储位是否存在
        WmsStorageLocations wmsStorageLocations = getById(id);
        if (wmsStorageLocations == null) {
            throw new JeecgBootException("储位不存在");
        }
        //储位状态为“创建”或禁用时方可启用
        if (!WarehouseDictEnum.STATUS_CREATED.getCode().equals(wmsStorageLocations.getStatus())
                && !WarehouseDictEnum.STATUS_INACTIVE.getCode().equals(wmsStorageLocations.getStatus())) {
            throw new JeecgBootException("储位状态为“创建”或禁用时方可启用");
        }
        wmsStorageLocations.setStatus(WarehouseDictEnum.STATUS_ACTIVE.getCode());
        updateById(wmsStorageLocations);

    }

    /**
     * 储位禁用
     *
     * @param id
     */
    @Override
    public void disable(String id) {
        //校验参数是否为空
        if (StringUtils.isEmpty(id)) {
            throw new JeecgBootException("参数不能为空");
        }
        //校验是否储位是否存在
        WmsStorageLocations wmsStorageLocations = getById(id);
        if (wmsStorageLocations == null) {
            throw new JeecgBootException("储位不存在");
        }
        //储位状态为“启用”时方可禁用
        if (!WarehouseDictEnum.STATUS_ACTIVE.getCode().equals(wmsStorageLocations.getStatus())) {
            throw new JeecgBootException("储位状态为“启用”时方可禁用");
        }
        wmsStorageLocations.setStatus(WarehouseDictEnum.STATUS_INACTIVE.getCode());
        updateById(wmsStorageLocations);

    }

    /**
     * 分页查询储位信息
     * @param wmsStorageLocations
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Override
    public IPage<WmsStorageLocations> queryPageList(WmsStorageLocations wmsStorageLocations, Integer pageNo, Integer pageSize) {
        Page<WmsStorageLocations> page = PageHelper.startPage(pageNo, pageSize);
        List<WmsStorageLocations> wmsStorageLocationsList = baseMapper.queryList(wmsStorageLocations);
        PageDTO<WmsStorageLocations> pageDTO = new PageDTO<>();
        pageDTO.setRecords(wmsStorageLocationsList);
        pageDTO.setCurrent(page.getPageNum());
        pageDTO.setSize(page.getPageSize());
        pageDTO.setTotal(page.getTotal());
        pageDTO.setPages(page.getPages());

        return pageDTO;

    }
}
