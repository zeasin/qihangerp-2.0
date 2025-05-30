import request from '@/utils/request'

// 查询淘宝订单列表
export function listGoodsSku(query) {
  return request({
    url: '/jd/goods/skuList',
    method: 'get',
    params: query
  })
}


export function getGoodsSku(id) {
  return request({
    url: '/jd/goods/sku/'+id,
    method: 'get',
  })
}


export function linkErpGoodsSkuId(data) {
  return request({
    url: '/jd/goods/sku/linkErp',
    method: 'post',
    data: data
  })
}

// 接口拉取淘宝商品
export function pullGoodsList(data) {
  return request({
    url: '/jd/goods/pull_goods',
    method: 'post',
    data: data
  })
}
