# 商户人员 API

统一响应外壳：`{ "code": 200, "msg": null, "data": ... }`。鉴权请求继续使用 `token` 请求头。

## 查询商户人员

- 方法与路径：`GET /merchant-staff`
- 权限：`user:select`
- Query：`pageNum`、`pageSize`、`blurParam`、`merchantId`、`roleId`、`status`
- 返回：`data.total` 与 `data.table`
- 排序：人员 ID 倒序

示例：

```json
{"code":200,"data":{"total":1,"table":[{"id":12,"name":"张三","username":"13800000000","roleId":1001,"roleName":"联系人","merchantId":17,"merchantName":"示例车商","status":1}]}}
```

## 查询详情

- 方法与路径：`GET /merchant-staff/{id}`
- 权限：`user:select`
- 不存在时：`404 / 资源不存在`

## 查询商户可选人员

- 方法与路径：`GET /merchant-staff/merchant/{merchantId}`
- 权限：`user:select`
- 排序：联系人、收款人、店员

## 查询业务角色

- 方法与路径：`GET /merchant-staff/roles`
- 返回固定业务角色，不授予系统权限：

```json
{"code":200,"data":[{"id":1001,"code":"CONTACT","name":"联系人"},{"id":1002,"code":"CLERK","name":"店员"},{"id":1003,"code":"PAYEE","name":"收款人"}]}
```

## 新增

- 方法与路径：`POST /merchant-staff`
- 权限：`user:update`
- Body：`name`、`username`（手机号）、`email`、`idNum`、`merchantId`、`roleId`、`status`
- 约束：`merchantId/name/roleId` 必填；一个商户只允许一个联系人。
- 冲突：`400 / 联系人重复`

## 更新

- 方法与路径：`PUT /merchant-staff`
- Body：同新增并增加 `id`
- 不存在时：`404 / 资源不存在`

## 删除

- 方法与路径：`DELETE /merchant-staff?id={id}`
- 权限：`user:update`
- 行为：同时软删除人员角色关系，不删除系统用户。

## 旧接口兼容

`GET /user/option/merchantId` 继续返回商户人员选项，供现有工单页面使用；系统用户管理仍使用 `/user/system`、`/user/approval/not` 等原路径。
