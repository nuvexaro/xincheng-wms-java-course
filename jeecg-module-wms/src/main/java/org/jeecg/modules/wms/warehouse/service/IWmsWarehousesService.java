package org.jeecg.modules.wms.warehouse.service;

import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 仓库表
 * @Author: jeecg-boot
 * @Date:   2026-07-08
 * @Version: V1.0
 */
public interface IWmsWarehousesService extends IService<WmsWarehouses> {
    /**
     * 新增仓库
     * @param wmsWarehouses
     */
    void add(WmsWarehouses wmsWarehouses);

    /**
     * 修改仓库
     * @param wmsWarehouses
     */
    void edit(WmsWarehouses wmsWarehouses);

    /**
     * 启用仓库
     * @param id
     */
    void enable(String id);

    /**
     * 禁用仓库
     * @param id
     */
    void disable(String id);
}
