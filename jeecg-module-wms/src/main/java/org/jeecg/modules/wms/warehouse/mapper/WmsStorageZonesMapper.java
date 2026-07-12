package org.jeecg.modules.wms.warehouse.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: 储区表
 * @Author: jeecg-boot
 * @Date:   2026-07-08
 * @Version: V1.0
 */
public interface WmsStorageZonesMapper extends BaseMapper<WmsStorageZones> {

    /**
     * 分页查询库区信息
     * @param wmsStorageZones
     * @return
     */
    public List<WmsStorageZones> queryList(WmsStorageZones wmsStorageZones);

}
