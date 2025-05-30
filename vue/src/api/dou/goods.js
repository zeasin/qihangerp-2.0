import request from '@/utils/request'

// 查询列表
export function listGoodsSku(query) {
  return request({
    url: '/dou/goods/skuList',
    method: 'get',
    params: query
  })
}


export function getGoodsSku(id) {
  return request({
    url: '/dou/goods/sku/'+id,
    method: 'get',
  })
}


export function linkErpGoodsSkuId(data) {
  return request({
    url: '/dou/goods/sku/linkErp',
    method: 'post',
    data: data
  })
}

// 接口拉取商品
export function pullGoodsList(data) {
  return request({
    url: '/dou/goods/pull_goods',
    method: 'post',
    data: data
  })
}
