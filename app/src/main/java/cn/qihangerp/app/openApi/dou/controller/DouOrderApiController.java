package cn.qihangerp.app.openApi.dou.controller;

import cn.qihangerp.app.openApi.dou.DouApiCommon;
import cn.qihangerp.common.AjaxResult;
import cn.qihangerp.common.ResultVoEnum;
import cn.qihangerp.common.enums.EnumShopType;
import cn.qihangerp.common.enums.HttpStatus;
import cn.qihangerp.common.mq.MqMessage;
import cn.qihangerp.common.mq.MqType;
import cn.qihangerp.common.mq.MqUtils;
import cn.qihangerp.domain.OShopPullLasttime;
import cn.qihangerp.domain.OShopPullLogs;
import cn.qihangerp.module.service.OShopPullLasttimeService;
import cn.qihangerp.module.service.OShopPullLogsService;

import cn.qihangerp.sdk.common.ApiResultVo;
import cn.qihangerp.sdk.dou.OrderApiHelper;
import cn.qihangerp.sdk.dou.PullRequest;
import cn.qihangerp.open.dou.domain.DouOrder;
import cn.qihangerp.open.dou.domain.DouOrderItem;
import cn.qihangerp.sdk.dou.response.DouOrderResponse;
import cn.qihangerp.open.dou.service.DouOrderService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 订单更新
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/open-api/dou/order")
public class DouOrderApiController {
    private static Logger log = LoggerFactory.getLogger(DouOrderApiController.class);

    private final DouOrderService orderService;
    private final DouApiCommon douApiCommon;
    private final MqUtils mqUtils;
    private final OShopPullLogsService pullLogsService;
    private final OShopPullLasttimeService pullLasttimeService;
    /**
     * 增量更新订单
     * @param req
     * @
     * @throws
     */
    @PostMapping("/pull_order")
    @ResponseBody
    public AjaxResult pullOrder(@RequestBody PullRequest req)   {
        log.info("/**************增量拉取dou订单****************/");
        if (req.getShopId() == null || req.getShopId() <= 0) {
            return AjaxResult.error(HttpStatus.PARAMS_ERROR, "参数错误，没有店铺Id");
        }
        Date currDateTime = new Date();
        Long currTimeMillis = System.currentTimeMillis();

        var checkResult = douApiCommon.checkBefore(req.getShopId());
        if (checkResult.getCode() != HttpStatus.SUCCESS) {
            return AjaxResult.error(checkResult.getCode(), checkResult.getMsg(),checkResult.getData());
        }

        String appKey = checkResult.getData().getAppKey();
        String appSecret = checkResult.getData().getAppSecret();
        Long douShopId = checkResult.getData().getSellerId();

        // 获取最后更新时间
        LocalDateTime startTime = null;
        LocalDateTime  endTime = null;
        OShopPullLasttime lasttime = pullLasttimeService.getLasttimeByShop(req.getShopId(), "ORDER");
        if(lasttime == null){
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(1);
        }else {
            startTime = lasttime.getLasttime().minusHours(1);//取上次结束一个小时前
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            long hours = duration.toHours();
            if (hours > 24) {
                // 大于24小时，只取24小时
                endTime = startTime.plusHours(24);
            } else {
                endTime = LocalDateTime.now();
            }
//            endTime = startTime.plusDays(1);//取24小时
//            if(endTime.isAfter(LocalDateTime.now())){
//                endTime = LocalDateTime.now();
//            }
        }
        String pullParams = "{startTime:"+startTime+",endTime:"+endTime+"}";
        //第一次获取
        ApiResultVo<DouOrderResponse> resultVo = OrderApiHelper.pullOrderList(appKey,appSecret,douShopId,startTime, endTime);

        if(resultVo.getCode() !=0 ){
            OShopPullLogs logs = new OShopPullLogs();
            logs.setShopId(req.getShopId());
            logs.setShopType(EnumShopType.DOU.getIndex());
            logs.setPullType("ORDER");
            logs.setPullWay("主动拉取订单");
            logs.setPullParams(pullParams);
            logs.setPullResult(resultVo.getMsg());
            logs.setPullTime(currDateTime);
            logs.setDuration(System.currentTimeMillis() - currTimeMillis);
            pullLogsService.save(logs);
            return AjaxResult.error("接口拉取错误："+resultVo.getMsg());
        }


        int insertSuccess = 0;//新增成功的订单
        int totalError = 0;
        int hasExistOrder = 0;//已存在的订单数

        //循环插入订单数据到数据库
        for (var order : resultVo.getList()) {
            DouOrder douOrder = new DouOrder();
            BeanUtils.copyProperties(order,douOrder);
            List<DouOrderItem> items = new ArrayList<>();
            if(order.getItems()!=null && order.getItems().size()>0){
                for (var item : order.getItems()) {
                    DouOrderItem douOrderItem = new DouOrderItem();
                    BeanUtils.copyProperties(item,douOrderItem);
                    items.add(douOrderItem);
                }
            }
            douOrder.setItems(items);
            //插入订单数据
            var result = orderService.saveOrder(req.getShopId(), douOrder);
            if (result.getCode() == ResultVoEnum.DataExist.getIndex()) {
                //已经存在
                log.info("/**************主动更新dou订单：开始更新数据库：" + order.getOrderId() + "存在、更新************开始通知****/");
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.DOU, MqType.ORDER_MESSAGE,order.getOrderId()));
                hasExistOrder++;
            } else if (result.getCode() == ResultVoEnum.SUCCESS.getIndex()) {
                log.info("/**************主动更新dou订单：开始更新数据库：" + order.getOrderId() + "不存在、新增************开始通知****/");
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.DOU,MqType.ORDER_MESSAGE,order.getOrderId()));
                insertSuccess++;
            } else {
                log.info("/**************主动更新dou订单：开始更新数据库：" + order.getOrderId() + "报错****************/");
                totalError++;
            }
        }

        if(totalError==0) {
            if (lasttime == null) {
                // 新增
                OShopPullLasttime insertLasttime = new OShopPullLasttime();
                insertLasttime.setShopId(req.getShopId());
                insertLasttime.setCreateTime(new Date());
                insertLasttime.setLasttime(endTime);
                insertLasttime.setPullType("ORDER");
                pullLasttimeService.save(insertLasttime);

            } else {
                // 修改
                OShopPullLasttime updateLasttime = new OShopPullLasttime();
                updateLasttime.setId(lasttime.getId());
                updateLasttime.setUpdateTime(new Date());
                updateLasttime.setLasttime(endTime);
                pullLasttimeService.updateById(updateLasttime);
            }
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        OShopPullLogs logs = new OShopPullLogs();
        logs.setShopType(EnumShopType.DOU.getIndex());
        logs.setShopId(req.getShopId());
        logs.setPullType("ORDER");
        logs.setPullWay("主动拉取订单");
        logs.setPullParams(pullParams);
        logs.setPullResult("{insert:"+insertSuccess+",update:"+hasExistOrder+",fail:"+totalError+"}");
        logs.setPullTime(currDateTime);
        logs.setDuration(System.currentTimeMillis() - currTimeMillis);
        pullLogsService.save(logs);

        String msg = "成功{startTime:"+startTime.format(df)+",endTime:"+endTime.format(df)+"}总共找到：" + resultVo.getTotalRecords() + "条订单，新增：" + insertSuccess + "条，添加错误：" + totalError + "条，更新：" + hasExistOrder + "条";
        log.info("/**************主动更新DOU订单：END：" + msg + "****************/");
        return AjaxResult.success(msg);
    }


    /**
     * 更新单个订单
     *
     * @param
     * @return
     * @throws
     */
    @RequestMapping("/pull_order_detail")
    @ResponseBody
    public AjaxResult getOrderPullDetail(@RequestBody PullRequest req)  {
        log.info("/**************主动更新dou订单by number****************/");
        if (req.getShopId() == null || req.getShopId() <= 0) {
            return AjaxResult.error(HttpStatus.PARAMS_ERROR, "参数错误，没有店铺Id");
        }
        if (!StringUtils.hasText(req.getOrderId())) {
            return AjaxResult.error(HttpStatus.PARAMS_ERROR, "参数错误，缺少orderId");
        }

        var checkResult = douApiCommon.checkBefore(req.getShopId());
        if (checkResult.getCode() != HttpStatus.SUCCESS) {
            return AjaxResult.error(checkResult.getCode(), checkResult.getMsg(), checkResult.getData());
        }

        String appKey = checkResult.getData().getAppKey();
        String appSecret = checkResult.getData().getAppSecret();
        Long douShopId = checkResult.getData().getSellerId();

        ApiResultVo<DouOrderResponse> resultVo = OrderApiHelper.pullOrderDetail( appKey, appSecret, douShopId,req.getOrderId());
        if (resultVo.getCode() == ResultVoEnum.SUCCESS.getIndex() && resultVo.getData()!=null) {
            DouOrder douOrder = new DouOrder();
            BeanUtils.copyProperties(resultVo.getData(),douOrder);
            List<DouOrderItem> items = new ArrayList<>();
            if(resultVo.getData().getItems()!=null && resultVo.getData().getItems().size()>0){
                for (var item : resultVo.getData().getItems()) {
                    DouOrderItem douOrderItem = new DouOrderItem();
                    BeanUtils.copyProperties(item,douOrderItem);
                    items.add(douOrderItem);
                }
            }
            douOrder.setItems(items);

            var result = orderService.saveOrder(req.getShopId(), douOrder);
            if (result.getCode() == ResultVoEnum.DataExist.getIndex()) {
                //已经存在
                log.info("/**************主动更新dou订单：开始更新数据库：" + resultVo.getData().getId() + "存在、更新****************/");
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.DOU, MqType.ORDER_MESSAGE,resultVo.getData().getOrderId()));
            } else if (result.getCode() == ResultVoEnum.SUCCESS.getIndex()) {
                log.info("/**************主动更新dou订单：开始更新数据库：" + resultVo.getData().getId() + "不存在、新增****************/");
                mqUtils.sendApiMessage(MqMessage.build(EnumShopType.DOU,MqType.ORDER_MESSAGE,resultVo.getData().getOrderId()));
            }
            return AjaxResult.success();
        } else {
            return AjaxResult.error(resultVo.getCode(), resultVo.getMsg());
        }
    }
}