/**
 * 工单详情金额回归样例，沿用详情接口的统一响应外壳及WorkorderDTO现有字段。
 * 覆盖截图合计、数字字符串、浮点尾差、零金额和缺失险种；仅用于离线联调，不拦截生产请求。
 */
export const moneyDisplayMock = {
  code: 200,
  msg: null,
  data: {
    commercialAmount: 2355.83,
    compulsoryAmount: 0,
    vehicleAndTaxAmount: 0,
    nonMotorAmount: 230,
    upstreamCommercialPercentage: 10,
    upstreamCommercialAmount: 235.58,
    upstreamCompulsoryAmount: 0,
    upstreamVehicleAndVesselTaxAmount: 0,
    upstreamNonMotorAmount: 23,
    downstreamCommercialAmount: '0.10',
    downstreamCompulsoryAmount: '0.20',
    downstreamVehicleAndVesselTaxAmount: null,
    downstreamNonMotorAmount: null,
    upstreamSumAmount: 258.58000000000004,
    downstreamSumAmount: '0.30'
  }
};
