package cn.qihangerp.app.openApi.tao.controller.feign;

import cn.qihangerp.common.AjaxResult;
import cn.qihangerp.open.tao.domain.TaoOrder;
import cn.qihangerp.open.tao.service.TaoOrderService;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/open-api/tao/order")
public class TaoOrderFeignController {
    private final TaoOrderService orderService;
    @GetMapping(value = "/get_detail")
    public AjaxResult getInfo(String tid)
    {
        TaoOrder order = orderService.queryDetailByTid(tid);
        if(order==null) return AjaxResult.error(404,"没有找到订单");
        else return AjaxResult.success(order);
    }
}