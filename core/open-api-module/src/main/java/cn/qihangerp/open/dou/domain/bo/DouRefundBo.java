package cn.qihangerp.open.dou.domain.bo;

import lombok.Data;

@Data
public class DouRefundBo {
    private Integer shopId;
    private String aftersaleId;
    private String orderId;
    private String aftersaleType;
}
