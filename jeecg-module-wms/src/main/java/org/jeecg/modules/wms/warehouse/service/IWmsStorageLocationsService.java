package org.jeecg.modules.wms.warehouse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 储位表
 * @Author: jeecg-boot
 * @Date:   2026-04-23
 * @Version: V1.0
 */
public interface IWmsStorageLocationsService extends IService<WmsStorageLocations> {

    /**
     * 分页查询储位信息
     * @param wmsStorageLocations 储位相关参数
     * @param pageNo 当前页面
     * @param pageSize  当前页大小
     * @return
     */
    IPage<WmsStorageLocations> queryPageList(WmsStorageLocations wmsStorageLocations, Integer pageNo, Integer pageSize);

    /**
     * 通过id启用储位
     * @param id
     */
    void enable(String id);

    /**
     * 通过id禁用储位
     * @param id
     */
    void disable(String id);
}
