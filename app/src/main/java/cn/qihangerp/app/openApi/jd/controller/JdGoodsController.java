package cn.qihangerp.app.openApi.jd.controller;


import cn.qihangerp.app.security.common.BaseController;
import cn.qihangerp.common.AjaxResult;
import cn.qihangerp.common.PageQuery;
import cn.qihangerp.common.PageResult;
import cn.qihangerp.common.TableDataInfo;
import cn.qihangerp.domain.OGoodsSku;
import cn.qihangerp.domain.bo.LinkErpGoodsSkuBo;
import cn.qihangerp.module.service.OGoodsSkuService;
import cn.qihangerp.open.jd.domain.JdGoods;
import cn.qihangerp.open.jd.domain.JdGoodsSku;
import cn.qihangerp.open.jd.domain.bo.JdGoodsBo;
import cn.qihangerp.open.jd.domain.vo.JdGoodsSkuListVo;
import cn.qihangerp.open.jd.service.JdGoodsService;
import cn.qihangerp.open.jd.service.JdGoodsSkuService;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/open-api/jd/goods")
@RestController
@AllArgsConstructor
public class JdGoodsController extends BaseController {
    private final JdGoodsService goodsService;
    private final JdGoodsSkuService skuService;
    private final OGoodsSkuService oGoodsSkuService;
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public TableDataInfo goodsList(JdGoodsBo bo, PageQuery pageQuery) {
        PageResult<JdGoods> result = goodsService.queryPageList(bo, pageQuery);

        return getDataTable(result);
    }

    @RequestMapping(value = "/skuList", method = RequestMethod.GET)
    public TableDataInfo skuList(JdGoodsBo bo, PageQuery pageQuery) {
        PageResult<JdGoodsSkuListVo> result = skuService.queryPageList(bo, pageQuery);

        return getDataTable(result);
    }

    /**
     * 获取店铺订单详细信息
     */
    @GetMapping(value = "/sku/{id}")
    public AjaxResult getSkuInfo(@PathVariable("id") Long id)
    {
        return AjaxResult.success(skuService.getById(id));
    }
    @PostMapping(value = "/sku/linkErp")
    public AjaxResult linkErp(@RequestBody LinkErpGoodsSkuBo bo)
    {
        if(StringUtils.isBlank(bo.getId())){
            return AjaxResult.error(500,"缺少参数Id");
        }
        if(StringUtils.isBlank(bo.getErpGoodsSkuId())){
            return AjaxResult.error(500,"缺少参数oGoodsSkuId");
        }
        OGoodsSku oGoodsSku = oGoodsSkuService.getById(bo.getErpGoodsSkuId());

        if(oGoodsSku == null) return AjaxResult.error(1500,"未找到系统商品sku");
        JdGoodsSku sku = new JdGoodsSku();
        sku.setId(bo.getId());
        sku.setOGoodsSkuId(Long.parseLong(bo.getErpGoodsSkuId()));
        skuService.updateById(sku);
        return success();
    }

}