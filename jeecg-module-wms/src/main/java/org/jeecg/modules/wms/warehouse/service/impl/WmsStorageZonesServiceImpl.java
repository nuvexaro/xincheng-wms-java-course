package org.jeecg.modules.wms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.mapper.WmsStorageZonesMapper;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageZonesService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 库区表
 * @Author: jeecg-boot
 * @Date:   2025-04-09
 * @Version: V1.0
 */
@Service
public class WmsStorageZonesServiceImpl extends ServiceImpl<WmsStorageZonesMapper, WmsStorageZones> implements IWmsStorageZonesService {

    @Override
    public IPage<WmsStorageZones> queryList(WmsStorageZones wmsStorageZones, Integer pageNo, Integer pageSize) {
        //查询条件对象不能为 null, 否则 xml 里的 zone.xxx 会报错
        if (wmsStorageZones == null) {
            wmsStorageZones = new WmsStorageZones();
        }
        //1. 设置分页参数
        int current = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        Page<WmsStorageZones> page = new Page<>(current, size);
        //2. 查询: 第一个参数是 Page 对象时, mybatis-plus 的分页插件会自动分页并统计总数
        return baseMapper.queryList(page, wmsStorageZones);
    }
}
