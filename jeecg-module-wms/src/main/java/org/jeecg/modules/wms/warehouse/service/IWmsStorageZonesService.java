package org.jeecg.modules.wms.warehouse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 储区表
 * @Author: jeecg-boot
 * @Date:   2026-07-08
 * @Version: V1.0
 */
public interface IWmsStorageZonesService extends IService<WmsStorageZones> {

    /**
     * 分页查询库区信息
     */
    IPage<WmsStorageZones> queryList(WmsStorageZones wmsStorageZones, Integer pageNo, Integer pageSize);

    /**
     *  库区启用
     * @param id
     */
    void enable(String id);

    /**
     *  库区禁用
     * @param id
     */
    void disable(String id);
}
