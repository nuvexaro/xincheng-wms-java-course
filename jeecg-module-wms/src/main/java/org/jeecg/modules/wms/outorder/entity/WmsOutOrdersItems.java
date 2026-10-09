package org.jeecg.modules.wms.outorder.entity;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @Description: 出库单明细表
 * @Version: V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@TableName("wms_out_orders_items")
@Schema(description="出库单明细表")
public class WmsOutOrdersItems implements Serializable {
    private static final long serialVersionUID = 1L;

    /**主键*/
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**创建人*/
    @Schema(description = "创建人")
    private String createBy;
    /**创建日期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;
    /**更新人*/
    @Schema(description = "更新人")
    private String updateBy;
    /**更新日期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
    /**所属部门*/
    @Schema(description = "所属部门")
    private String sysOrgCode;
    /**出库单id*/
    @Schema(description = "出库单id")
    private String orderId;
    /**商品id*/
    @Schema(description = "商品id")
    private String skuId;
    /**预期出库数量*/
    @Schema(description = "预期出库数量")
    private Integer expectedQuantity;
    /**分配数量*/
    @Schema(description = "分配数量")
    private Integer allocatedQuantity;
    /**拣货数量*/
    @Schema(description = "拣货数量")
    private Integer pickedQuantity;
    /**打包数量*/
    @Schema(description = "打包数量")
    private Integer packedQuantity;
    /**批次号*/
    @Schema(description = "批次号")
    private String batchNumber;
    /**保质期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @Schema(description = "保质期")
    private Date expiryDate;
    /**状态*/
    @Schema(description = "状态")
    private String status;
    /**商品名称(非数据库字段, 页面显示用)*/
    @TableField(exist = false)
    @Schema(description = "商品名称(非数据库字段, 页面显示用)")
    private String productName;
    /**商品编码(非数据库字段, 页面显示用)*/
    @TableField(exist = false)
    @Schema(description = "商品编码(非数据库字段, 页面显示用)")
    private String productCode;
}
