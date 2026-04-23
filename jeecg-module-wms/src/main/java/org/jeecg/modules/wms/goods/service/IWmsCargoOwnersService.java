package org.jeecg.modules.wms.goods.service;

import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 货主表
 * @Author: jeecg-boot
 * @Date:   2026-04-22
 * @Version: V1.0
 */
public interface IWmsCargoOwnersService extends IService<WmsCargoOwners> {

    /**
     * 自动生成的代码中货主编码需要人工输入，并且保证编码唯一，在使用时非常不方便。我们现在要实现货主编码自动生成并且保证唯一性。
     * 生成方案：C+5位序号，序号使用redis自增序号实现
     * @param wmsCargoOwners
     */
    void add(WmsCargoOwners wmsCargoOwners);
}
