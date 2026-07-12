package org.jeecg.modules.wms.warehouse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 储位表
 * @Author: jeecg-boot
 * @Date:   2026-07-08
 * @Version: V1.0
 */
public interface IWmsStorageLocationsService extends IService<WmsStorageLocations> {

    /**
     * 储位启用
     * @param id
     */
    void enable(String id);

    /**
     * 储位禁用
     * @param id
     */
    void disable(String id);

    /**
     *  查询
     * @param wmsStorageLocations
     * @param pageNo
     * @param pageSize
     * @return
     */
    IPage<WmsStorageLocations> queryPageList(WmsStorageLocations wmsStorageLocations, Integer pageNo, Integer pageSize);
}
