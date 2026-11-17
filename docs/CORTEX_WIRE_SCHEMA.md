# Cortex protobuf 字段结构核验

基于先前已提供的墨墨 5.6.10 静态报告，接口候选路径、部分字段名和枚举名称已知。但仓库当前没有真实 Cortex 请求体或原始 APK，尚不具备直接调用的完整协议证据。

新增离线工具 tools/cortex_wire_schema.py ：

- 从本人正常操作的本地 HAR，解析明确声明 base64 的 protobuf 请求体；仅报告字段编号、wire type、重复次数和可能的嵌套。
- 不输出原始请求体、任何 protobuf 字段值、Token、签名、单词 ID、查询参数或响应正文。
- length-delimited 字段可能是字符串/bytes/packed repeated；嵌套只是语法候选，不是已验证的 protobuf 类型。
- 对没有编码说明、格式畸形或不属于已知 Cortex 路径的条目不做猜测。不会产生 HTTP 请求。

运行： python3 tools/cortex_wire_schema.py private-study.har --out cortex-structure.json
测试： python3 -m unittest discover -s tools -p 'test_cortex_wire_schema.py' -v

请把原始 HAR、Token、签名值保留在个人设备，不上传源码或聊天。结构摘要也需人工检查再分享。

该阶段推进的是字段级协议提取能力。它不能从 APK 字符串自动推导 protobuf 枚举数值、认证、请求顺序、checkpoint 冲突语义。
现有 CortexUnconfiguredSender 继续禁写，原流体云/通知前台流程不受影响。
