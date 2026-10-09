package org.jeecg.modules.wms.outorder.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: WmsOutOrdersItems
 * @Version: V1.0
 */
public interface WmsOutOrdersItemsMapper extends BaseMapper<WmsOutOrdersItems> {

    /**
     * 通过出库单id删除
     */
    int deleteByMainId(@Param("mainId") String mainId);

    /**
     * 通过出库单id查询(带商品名称)
     */
    List<WmsOutOrdersItems> selectByMainId(@Param("mainId") String mainId);
}
