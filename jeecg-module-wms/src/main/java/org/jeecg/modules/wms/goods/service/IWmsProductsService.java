package org.jeecg.modules.wms.goods.service;

import org.jeecg.modules.wms.goods.entity.WmsProducts;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import org.jeecg.modules.wms.config.ExcelImportResult;
import org.jeecg.modules.wms.goods.excel.WmsProductsImport;

/**
 * @Description: 商品信息表
 * @Author: jeecg-boot
 * @Date:   2025-04-14
 * @Version: V1.0
 */

public interface IWmsProductsService extends IService<WmsProducts> {

    /**
     * 添加商品
     */
    void add(WmsProducts wmsProducts);

    /**
     * 修改商品
     */
    void edit(WmsProducts wmsProducts);

    /**
     * 导入商品: Excel 里填的是货主名称、分类名称、品牌名称, 这里转换成对应的 id 再保存
     * 商品已存在(同一货主下商品编码相同)则更新, 不存在则新增
     *
     * @param cachedDataList 从 Excel 读到的一批商品
     * @param result         导入结果, 成功/失败的条数和原因会累加到这个对象里
     */
    void importProduct(List<WmsProductsImport> cachedDataList, ExcelImportResult result);
}
