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
import cn.qihangerp.open.tao.domain.TaoOrder;
import cn.qihangerp.open.tao.domain.bo.TaoOrderBo;
import cn.qihangerp.open.tao.domain.bo.TaoOrderPushBo;
import cn.qihangerp.open.tao.service.TaoOrderService;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/open-api/tao/order")
public class TaoOrderController extends BaseController {
    private final TaoOrderService orderService;
    private final MqUtils mqUtils;
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public TableDataInfo goodsList(TaoOrderBo bo, PageQuery pageQuery) {
        PageResult<TaoOrder> result = orderService.queryPageList(bo, pageQuery);

        return getDataTable(result);
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(orderService.queryDetailById(id));
    }

    /**
     * 手动推送到系统
     * @param bo
     * @return
     */
    @PostMapping("/push_oms")
    @ResponseBody
    public AjaxResult pushOms(@RequestBody TaoOrderPushBo bo) {
        // TODO:需要优化消息格式
        if(bo!=null && bo.getIds()!=null) {
            for(String id: bo.getIds()) {
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.TAO, MqType.ORDER_MESSAGE, id));
            }
        }
        return success();
    }
}