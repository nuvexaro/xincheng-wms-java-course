package org.jeecg.modules.wms.outorder.vo;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;

/**
 * @Description: 出库单页面提交的数据 = 出库单主表的字段 + 出库单明细列表
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmsOutOrdersPage extends WmsOutOrders {
    private static final long serialVersionUID = 1L;

    /**出库单明细*/
    private List<WmsOutOrdersItems> wmsOutOrdersItemsList;
}
