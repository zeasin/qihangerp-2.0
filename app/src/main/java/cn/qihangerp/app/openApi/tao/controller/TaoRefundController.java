package cn.qihangerp.app.openApi.tao.controller;

import cn.qihangerp.app.security.common.BaseController;
import cn.qihangerp.common.AjaxResult;
import cn.qihangerp.common.PageQuery;
import cn.qihangerp.common.PageResult;
import cn.qihangerp.common.TableDataInfo;
import cn.qihangerp.common.enums.EnumShopType;
import cn.qihangerp.common.mq.MqMessage;
import cn.qihangerp.common.mq.MqType;
import cn.qihangerp.common.mq.MqUtils;
import cn.qihangerp.open.tao.domain.TaoRefund;
import cn.qihangerp.open.tao.domain.bo.TaoOrderPushBo;
import cn.qihangerp.open.tao.domain.bo.TaoRefundBo;
import cn.qihangerp.open.tao.service.TaoRefundService;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/open-api/tao/refund")
public class TaoRefundController extends BaseController {
    private final TaoRefundService refundService;
    private final MqUtils mqUtils;
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public TableDataInfo goodsList(TaoRefundBo bo, PageQuery pageQuery) {
        PageResult<TaoRefund> result = refundService.queryPageList(bo, pageQuery);

        return getDataTable(result);
    }

    @PostMapping("/push_oms")
    @ResponseBody
    public AjaxResult pushOms(@RequestBody TaoOrderPushBo bo) {
        // TODO:需要优化消息格式
        if(bo!=null && bo.getIds()!=null) {
            for(String id: bo.getIds()) {
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.TAO, MqType.REFUND_MESSAGE, id));
            }
        }
        return success();
    }
}