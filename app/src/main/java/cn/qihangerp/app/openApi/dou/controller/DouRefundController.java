package cn.qihangerp.app.openApi.dou.controller;

import cn.qihangerp.app.security.common.BaseController;
import cn.qihangerp.common.AjaxResult;
import cn.qihangerp.common.PageQuery;
import cn.qihangerp.common.PageResult;
import cn.qihangerp.common.TableDataInfo;
import cn.qihangerp.common.enums.EnumShopType;
import cn.qihangerp.common.mq.MqMessage;
import cn.qihangerp.common.mq.MqType;
import cn.qihangerp.common.mq.MqUtils;
import cn.qihangerp.open.dou.domain.DouRefund;
import cn.qihangerp.open.dou.domain.bo.DouOrderPushBo;
import cn.qihangerp.open.dou.domain.bo.DouRefundBo;
import cn.qihangerp.open.dou.service.DouRefundService;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/open-api/dou/refund")
public class DouRefundController extends BaseController {
    private final DouRefundService refundService;
    private final MqUtils mqUtils;
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public TableDataInfo goodsList(DouRefundBo bo, PageQuery pageQuery) {
        PageResult<DouRefund> result = refundService.queryPageList(bo, pageQuery);

        return getDataTable(result);
    }

    @PostMapping("/push_oms")
    @ResponseBody
    public AjaxResult pushOms(@RequestBody DouOrderPushBo bo) {
        // TODO:需要优化消息格式
        if(bo!=null && bo.getIds()!=null) {
            for(String id: bo.getIds()) {
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.DOU, MqType.REFUND_MESSAGE, id));
            }
        }
        return success();
    }
}