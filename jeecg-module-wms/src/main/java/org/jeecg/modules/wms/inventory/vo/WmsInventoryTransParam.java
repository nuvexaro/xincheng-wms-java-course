package org.jeecg.modules.wms.inventory.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @description 库存变更参数类: 调用库存变更方法时传的参数
 * @version 1.0
 */
@Data
public class WmsInventoryTransParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**商品id*/
    private String productId;
    /**执行数量*/
    private Integer execQuantity;
    /**仓库id*/
    private String warehouseId;
    /**来源储位编码(上架时: 收货时放的储位)*/
    private String sourceLocationCode;
    /**目的储位编码*/
    private String targetLocationCode;
    /**批次号*/
    private String batchNumber;
    /**保质期*/
    private Date expiryDate;
    /**备注(关联的任务号)*/
    private String remarks;
    /**变更类型*/
    private String transactionType;
    /**执行人*/
    private String operator;
    /**执行时间*/
    private Date operationTime;
    /**是否可售: 1可售 0不可售*/
    private String isSellable;
}
