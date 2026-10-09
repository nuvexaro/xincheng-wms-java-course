package org.jeecg.modules.wms.inventory.service;

import org.jeecg.modules.wms.inventory.entity.WmsInventoryTrans;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 库存变更
 *               不同业务(收货、上架、拣货...)的库存变更规则不同, 每种业务一个实现类:
 *               WmsInventoryTransByReceiving 收货, WmsInventoryTransByPutaway 上架
 *               因为有多个实现类, 使用时要按具体的实现类注入, 不能按这个接口注入
 * @Version: V1.0
 */
public interface IWmsInventoryTransService extends IService<WmsInventoryTrans> {

    /**
     * 库存变更
     *
     * @param inventoryTransParam 库存变更参数
     */
    void transfer(WmsInventoryTransParam inventoryTransParam);
}
