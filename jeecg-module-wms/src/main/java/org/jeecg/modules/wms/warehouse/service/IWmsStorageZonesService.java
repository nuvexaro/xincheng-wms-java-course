package org.jeecg.modules.wms.warehouse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 储区表
 * @Author: jeecg-boot
 * @Date:   2026-04-23
 * @Version: V1.0
 */
public interface IWmsStorageZonesService extends IService<WmsStorageZones> {

    /**
     * 分页查询
     * @param wmsStorageZones 储区相关参数
     * @param pageNo 当前页面
     * @param pageSize 当代页大小
     * @return
     */
    IPage<WmsStorageZones> queryPageList(WmsStorageZones wmsStorageZones, Integer pageNo, Integer pageSize);
}
