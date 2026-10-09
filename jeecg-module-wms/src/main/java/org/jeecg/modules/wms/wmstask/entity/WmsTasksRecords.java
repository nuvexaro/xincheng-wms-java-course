package org.jeecg.modules.wms.wmstask.entity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.jeecg.common.constant.ProvinceCityArea;
import org.jeecg.common.util.SpringContextUtils;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecg.common.aspect.annotation.Dict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 任务执行记录表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@TableName("wms_tasks_records")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description="任务执行记录表")
public class WmsTasksRecords implements Serializable {
    private static final long serialVersionUID = 1L;

	/**主键*/
	@TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private java.lang.String id;
	/**创建人*/
    @Schema(description = "创建人")
    private java.lang.String createBy;
	/**创建日期*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private java.util.Date createTime;
	/**更新人*/
    @Schema(description = "更新人")
    private java.lang.String updateBy;
	/**更新日期*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private java.util.Date updateTime;
	/**所属部门*/
    @Schema(description = "所属部门")
    private java.lang.String sysOrgCode;
	/**任务号*/
	@Excel(name = "任务号", width = 15)
    @Schema(description = "任务号")
    private java.lang.String taskNumber;
	/**任务类型*/
	@Excel(name = "任务类型", width = 15)
    @Schema(description = "任务类型")
    @Dict(dicCode = "task_type")
    private java.lang.String taskType;
	/**商品id*/
	@Excel(name = "商品id", width = 15)
    @Schema(description = "商品id")
    private java.lang.String productId;
	/**执行数量*/
	@Excel(name = "执行数量", width = 15)
    @Schema(description = "执行数量")
    private java.lang.Integer execQuantity;
	/**来源储位编码*/
	@Excel(name = "来源储位编码", width = 15)
    @Schema(description = "来源储位编码")
    private java.lang.String sourceLocationCode;
	/**目的储位编码*/
	@Excel(name = "目的储位编码", width = 15)
    @Schema(description = "目的储位编码")
    private java.lang.String targetLocationCode;
	/**来源容器编码*/
	@Excel(name = "来源容器编码", width = 15)
    @Schema(description = "来源容器编码")
    private java.lang.String sourceContainerCode;
	/**目的容器编码*/
	@Excel(name = "目的容器编码", width = 15)
    @Schema(description = "目的容器编码")
    private java.lang.String targetContainerCode;
	/**执行人*/
	@Excel(name = "执行人", width = 15)
    @Schema(description = "执行人")
    private java.lang.String operator;
	/**执行时间*/
	@Excel(name = "执行时间", width = 20, format = "yyyy-MM-dd HH:mm:ss")
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "执行时间")
    private java.util.Date operationTime;
	/**入库单id*/
	@Excel(name = "入库单id", width = 15)
    @Schema(description = "入库单id")
    private java.lang.String stockInOrderId;
	/**入库明细id*/
	@Excel(name = "入库明细id", width = 15)
    @Schema(description = "入库明细id")
    private java.lang.String stockInOrderItemId;
	/**波次单id*/
	@Excel(name = "波次单id", width = 15)
    @Schema(description = "波次单id")
    private java.lang.String waveOrderId;
	/**库存属性*/
	@Excel(name = "库存属性", width = 15)
    @Schema(description = "库存属性")
    private java.lang.String inventoryAttribute;
	/**任务id*/
	@Excel(name = "任务id", width = 15)
    @Schema(description = "任务id")
    private java.lang.String taskId;
	/**批次号*/
	@Excel(name = "批次号", width = 15)
    @Schema(description = "批次号")
    private java.lang.String batchNumber;
	/**保质期*/
	@Excel(name = "保质期", width = 15, format = "yyyy-MM-dd")
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @Schema(description = "保质期")
    private java.util.Date expiryDate;
	/**出库单id*/
	@Excel(name = "出库单id", width = 15)
    @Schema(description = "出库单id")
    private java.lang.String outOrderId;
	/**波次拣货明细id*/
	@Excel(name = "波次拣货明细id", width = 15)
    @Schema(description = "波次拣货明细id")
    private java.lang.String waveSkuSummaryId;
	/**来源仓库*/
	@Excel(name = "来源仓库", width = 15)
    @Schema(description = "来源仓库")
    private java.lang.String sourceWarehouseId;
	/**目的仓库*/
	@Excel(name = "目的仓库", width = 15)
    @Schema(description = "目的仓库")
    private java.lang.String targetWarehouseId;

    // ==================== 以下为非数据库字段 ====================
    /**入库单号(关联查询, 页面显示用)*/
    @TableField(exist = false)
    @Schema(description = "入库单号")
    private java.lang.String stockInOrderNumber;
    /**商品名称(关联查询, 页面显示用)*/
    @TableField(exist = false)
    @Schema(description = "商品名称")
    private java.lang.String productName;
    /**货主名称(关联查询, 页面显示用)*/
    @TableField(exist = false)
    @Schema(description = "货主名称")
    private java.lang.String ownerName;
    /**仓库名称(关联查询, 页面显示用)*/
    @TableField(exist = false)
    @Schema(description = "仓库名称")
    private java.lang.String warehouseName;
    /**收货日期(前端收货表单填写, 后端把它作为执行时间 operationTime 保存)*/
    @TableField(exist = false)
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "收货日期")
    private java.util.Date receivingDate;
}
