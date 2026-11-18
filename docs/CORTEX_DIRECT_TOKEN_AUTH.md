# Cortex 直接导入授权（2026-11-18）

此前 UI 只支持手工输入 `legacy_token` 然后调用 `/api/v1/tokens/exchange`，对已经从本人登录态观察到 Cortex Bearer 的用户造成不必要的障碍。本次改为：

1. **方式一：直接导入 Cortex Bearer Token**，可粘贴 `Bearer …` 或仅 Token。
2. 先请求本人已确认的 **只读** `GET /api/v1/study/checkpoints`，验证通过才使用 Android Keystore AES-GCM 保存；失败保持已有凭证不变，不落盘。
3. **方式二：Legacy Token 交换**，沿用旧实现，但交换后同样做只读验证才保存。
4. 原有墨墨开放 API Token 继续独立管理；不能拿它替代 Cortex Bearer。原有通知、流体云、前台点击与三态事务均未改动。

## 用户已有的逆向材料

- `mm_runtime_dump_v2.js` 已存在 `HttpBearerAuth.getBearerToken` 和 OkHttp request 日志采集逻辑。该脚本可能输出真实凭证和请求体；不得将原始结果传入聊天、GitHub、日志共享或公开 issue。
- `墨墨Cortex协议还原报告.md` 的网关响应证明 `/api/v1/tokens/exchange` 收 `legacy_token`、其他 Cortex 只读接口需要 Bearer。但不能据此推断某个登录状态一定能交换，也不能宣称 App 的私有协议公开支持第三方写回。
- Web 端是否实际存在 `tokens/exchange` 请求、实际 Token 来源和生命周期仍需本人设备验证。

## 当前交付边界

只是**账号授权便捷性与只读连通性**。尚未启用 `/api/v1/study/oplog` 写回；认证成功不能代替真实字段号、同步顺序、操作幂等、同词官方回读的端到端验证。
