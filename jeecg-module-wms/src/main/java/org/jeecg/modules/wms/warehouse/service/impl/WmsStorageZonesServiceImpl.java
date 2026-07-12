package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.mapper.WmsStorageZonesMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageZonesService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

/**
 * @Description: 储区表
 * @Author: jeecg-boot
 * @Date:   2026-07-08
 * @Version: V1.0
 */
@Service
@Slf4j
public class WmsStorageZonesServiceImpl extends ServiceImpl<WmsStorageZonesMapper, WmsStorageZones> implements IWmsStorageZonesService {

    /**
     * 分页查询库区信息
     *
     * 这是一个自己写的分页，没有使用mybatis-plus的分页，而是使用PageHelper
     * 为什么要自己写一个，因为原来的分页，不能实现多表查询 储位表只有仓库id，我要获取仓库名称
     * 但其实我也有点纳闷，多表查询mp不能实现吗？
     * 实现思路：调用PageHelper.startPage() -> 调用自己写的queryList() -> 将结果封装到PageDTO<WmsStorageZones> -> 返回结果
     * 为什么可以直接返回？ 因为PageDTO是IPage的子类
     *
     * @param wmsStorageZones
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Override
    public IPage<WmsStorageZones> queryList(WmsStorageZones wmsStorageZones, Integer pageNo, Integer pageSize) {
        Page<WmsStorageZones> page = PageHelper.startPage(pageNo, pageSize);
        List<WmsStorageZones> wmsStorageZonesList = baseMapper.queryList(wmsStorageZones);

        //转换wmsStorageZonesList -> PageDTO<WmsStorageZones>
        PageDTO<WmsStorageZones> wmsStorageZonesPageDTO = new PageDTO<>();
        wmsStorageZonesPageDTO.setRecords(wmsStorageZonesList);
        wmsStorageZonesPageDTO.setCurrent(page.getPageNum());
        wmsStorageZonesPageDTO.setSize(page.getPageSize());
        wmsStorageZonesPageDTO.setTotal(page.getTotal());
        wmsStorageZonesPageDTO.setPages(page.getPages());

        return wmsStorageZonesPageDTO;
    }

    /**
     * 启用
     * @param id
     */
    @Override
    public void enable(String id) {
        //校验参数是否为空
        if (StringUtils.isEmpty(id)) {
            throw new JeecgBootException("参数不能为空");
        }
        //校验是否仓库是否存在
        WmsStorageZones wmsStorageZones = getById(id);
        if (wmsStorageZones == null) {
            throw new JeecgBootException("库区不存在");
        }
        //库区状态为“创建”或禁用时方可启用
        if (!WarehouseDictEnum.STATUS_CREATED.getCode().equals(wmsStorageZones.getStatus())
                && !WarehouseDictEnum.STATUS_INACTIVE.getCode().equals(wmsStorageZones.getStatus())) {
            throw new JeecgBootException("库区状态为“创建”或禁用时方可启用");
        }
        wmsStorageZones.setStatus(WarehouseDictEnum.STATUS_ACTIVE.getCode());
        updateById(wmsStorageZones);

    }

    /**
     *  禁用
     * @param id
     */
    @Override
    public void disable(String id) {
        //校验参数是否为空
        if (StringUtils.isEmpty(id)) {
            throw new JeecgBootException("参数不能为空");
        }
        //校验是否库区是否存在
        WmsStorageZones wmsStorageZones = getById(id);
        if (wmsStorageZones == null) {
            throw new JeecgBootException("库区不存在");
        }
        //库区状态为“启用”时方可禁用
        if (!WarehouseDictEnum.STATUS_ACTIVE.getCode().equals(wmsStorageZones.getStatus())) {
            throw new JeecgBootException("库区状态为“启用”时方可禁用");
        }
        wmsStorageZones.setStatus(WarehouseDictEnum.STATUS_INACTIVE.getCode());
        updateById(wmsStorageZones);

    }
}
