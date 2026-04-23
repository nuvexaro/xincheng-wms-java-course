package org.jeecg.modules.wms.goods.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.mapper.WmsCargoOwnersMapper;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 货主表
 * @Author: jeecg-boot
 * @Date:   2026-04-22
 * @Version: V1.0
 */
@Service
public class WmsCargoOwnersServiceImpl extends ServiceImpl<WmsCargoOwnersMapper, WmsCargoOwners> implements IWmsCargoOwnersService {

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void add(WmsCargoOwners wmsCargoOwners) {
        wmsCargoOwners.setOwnerCode(generateOwnerCode());
        this.save(wmsCargoOwners);
    }

    private String generateOwnerCode() {
        long incr = 0;
        try{
            incr =  redisUtil.incr("WMS_CARGO_OWNERS_CODE", 1L);
        }catch (Exception e){
            log.error("生成货主编码失败", e);
            throw new JeecgBootException("生成货主编码失败");
        }

        return "C" + String.format("%05d", incr);

    }
}
