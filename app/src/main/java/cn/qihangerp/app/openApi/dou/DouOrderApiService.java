package cn.qihangerp.app.openApi.dou;

import cn.qihangerp.common.ResultVoEnum;
import cn.qihangerp.common.enums.EnumShopType;
import cn.qihangerp.common.mq.MqMessage;
import cn.qihangerp.common.mq.MqType;
import cn.qihangerp.common.mq.MqUtils;
import cn.qihangerp.domain.OShopPullLasttime;
import cn.qihangerp.domain.OShopPullLogs;
import cn.qihangerp.module.service.OShopPullLasttimeService;
import cn.qihangerp.module.service.OShopPullLogsService;
import cn.qihangerp.sdk.common.ApiResultVo;
import cn.qihangerp.sdk.dou.OrderApiHelper;
import cn.qihangerp.open.dou.domain.DouOrder;
import cn.qihangerp.sdk.dou.response.DouOrderResponse;
import cn.qihangerp.open.dou.service.DouOrderService;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Log
@AllArgsConstructor
@Service
public class DouOrderApiService {
    private final OShopPullLasttimeService pullLasttimeService;
    private final OShopPullLogsService pullLogsService;
    private final DouOrderService orderService;
    private final MqUtils mqUtils;

    public void pullOrder(String pullWay,Long shopId,Long douShopId,String appKey,String appSecret){
        log.info("/**************增量拉取dou订单****************/");

        Date currDateTime = new Date();
        Long currTimeMillis = System.currentTimeMillis();


        // 获取最后更新时间
        LocalDateTime startTime = null;
        LocalDateTime  endTime = null;
        OShopPullLasttime lasttime = pullLasttimeService.getLasttimeByShop(shopId, "ORDER");
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
            logs.setShopId(shopId);
            logs.setShopType(EnumShopType.DOU.getIndex());
            logs.setPullType("ORDER");
            logs.setPullWay(pullWay);
            logs.setPullParams(pullParams);
            logs.setPullResult(resultVo.getMsg());
            logs.setPullTime(currDateTime);
            logs.setDuration(System.currentTimeMillis() - currTimeMillis);
            pullLogsService.save(logs);
            return;
        }


        int insertSuccess = 0;//新增成功的订单
        int totalError = 0;
        int hasExistOrder = 0;//已存在的订单数

        //循环插入订单数据到数据库
        for (var order : resultVo.getList()) {
            DouOrder douOrder = new DouOrder();
            BeanUtils.copyProperties(order,douOrder);
            //插入订单数据
            var result = orderService.saveOrder(shopId, douOrder);
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

        if(lasttime == null){
            // 新增
            OShopPullLasttime insertLasttime = new OShopPullLasttime();
            insertLasttime.setShopId(shopId);
            insertLasttime.setCreateTime(new Date());
            insertLasttime.setLasttime(endTime);
            insertLasttime.setPullType("ORDER");
            pullLasttimeService.save(insertLasttime);

        }else {
            // 修改
            OShopPullLasttime updateLasttime = new OShopPullLasttime();
            updateLasttime.setId(lasttime.getId());
            updateLasttime.setUpdateTime(new Date());
            updateLasttime.setLasttime(endTime);
            pullLasttimeService.updateById(updateLasttime);
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        OShopPullLogs logs = new OShopPullLogs();
        logs.setShopType(EnumShopType.DOU.getIndex());
        logs.setShopId(shopId);
        logs.setPullType("ORDER");
        logs.setPullWay(pullWay);
        logs.setPullParams(pullParams);
        logs.setPullResult("{insert:"+insertSuccess+",update:"+hasExistOrder+",fail:"+totalError+"}");
        logs.setPullTime(currDateTime);
        logs.setDuration(System.currentTimeMillis() - currTimeMillis);
        pullLogsService.save(logs);

        String msg = "成功{startTime:"+startTime.format(df)+",endTime:"+endTime.format(df)+"}总共找到：" + resultVo.getTotalRecords() + "条订单，新增：" + insertSuccess + "条，添加错误：" + totalError + "条，更新：" + hasExistOrder + "条";
        log.info(pullWay+"/**************更新DOU订单：END：" + msg + "****************/");

    }
}