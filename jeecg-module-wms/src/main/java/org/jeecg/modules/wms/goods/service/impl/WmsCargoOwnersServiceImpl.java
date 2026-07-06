package org.jeecg.modules.wms.goods.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.mapper.WmsCargoOwnersMapper;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Description: 货主表
 * @Author: jeecg-boot
 * @Date:   2026-07-05
 * @Version: V1.0
 */
@Service
public class WmsCargoOwnersServiceImpl extends ServiceImpl<WmsCargoOwnersMapper, WmsCargoOwners> implements IWmsCargoOwnersService {

    private static final String OWNER_CODE_SEQ_KEY = "wms:cargo-owner:code-seq";
    private static final String OWNER_CODE_PREFIX = "C";
    private static final int OWNER_CODE_LENGTH = 5;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public String generateOwnerCode() {
        String ownerCode;
        do {
            long seq = redisUtil.incr(OWNER_CODE_SEQ_KEY, 1);
            ownerCode = OWNER_CODE_PREFIX + String.format("%0" + OWNER_CODE_LENGTH + "d", seq);
        } while (isOwnerCodeExists(ownerCode));
        return ownerCode;
    }

    private boolean isOwnerCodeExists(String ownerCode) {
        return this.count(new LambdaQueryWrapper<WmsCargoOwners>()
                .eq(WmsCargoOwners::getOwnerCode, ownerCode)) > 0;
    }

}
