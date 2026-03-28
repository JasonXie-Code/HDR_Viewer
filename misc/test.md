# HDR Viewer — ADB 亮度实测记录

**设备**：`192.168.88.161:35061`（`product:haotian`，`model:2410DPN6CC`）  
**说明**：应用内亮度为 **0～200%** 单一滑块，`slider01 = 亮度% / 200`；**100% = 中点**，左侧为系统亮度区，右侧为 HDR 增强区（`slider01 > 0.5`）。

---

## 第一轮：亮度 **100%**（中点，第一段末端）

**应用语义**：`slider01 = 0.5`，**未进入第二段**（第二段需 `> 0.5`）。预期无 `COLOR_MODE_HDR`、无 `setDesiredHdrHeadroom`、无 `SdrPreviewTone` 强度。

| 项目 | 值 |
|------|-----|
| `settings get system screen_brightness` | 255 |
| `screen_brightness_mode` | 0（手动） |
| `Display Brightness` | ≈0.250 |
| `mBrightness` | ≈0.250 |
| `mUnthrottledBrightness` | 1.0 |
| `mHbmStatsState` | **HBM_OFF** |
| `mHbmMode` | **off** |
| `MainActivity` 窗口 | `sbrt=1.0`（**无** `colorMode=COLOR_MODE_HDR`、**无** `desiredHdrHeadroom`） |
| SurfaceFlinger HDR 日志尾部 | `desiredRatio` 自高向低衰减；末帧出现 **`numHdrLayers(0), desiredRatio(0.00)`**（符合离开 HDR 或动画收尾） |

**结论**：100% 为**纯系统亮度 + 窗口 `sbrt=1.0`**，**HBM 未因 HDR 开启**；与产品「中点左侧」定义一致。

---

## 第二轮：亮度 **120%**（第二段约 20%）

**应用语义**：`slider01 = 0.6`，第二段强度 `(0.6−0.5)/0.5 = 0.2`；`headroomNitsForStrength(0.2) = 1 + 0.2×5 = 2.0`（动画中可略低）。

| 项目 | 值 |
|------|-----|
| `settings get system screen_brightness` | 255 |
| `screen_brightness_mode` | 0（手动） |
| `Display Brightness` | ≈0.487 |
| `mBrightness` | ≈0.250 |
| `mUnthrottledBrightness` | 1.0 |
| `mHbmStatsState` | **HBM_ON_HDR** |
| `mHbmMode` | **hdr(0.4874084)**（与 `Display Brightness` 一致） |
| `mCachedBrightnessInfo.hbmMode` | 2 |
| `MainActivity` 窗口 | `sbrt=1.0` **`colorMode=COLOR_MODE_HDR`** **`desiredHdrHeadroom≈1.95`** |
| SurfaceFlinger | `numHdrLayers(1)`；主层 **`current/desired hdr/sdr ratio = 1.95`**；**`dataspace=0x188a0000`**（扩展范围路径） |

**结论**：120% 已进第二段，**HDR 窗口 + headroom + `HBM_ON_HDR`** 均被系统识别；**hdr/sdr 比约 1.95×**（低于 200% 满档时的更高比值属预期）。

---

## 第三轮：亮度 **140%**（第二段约 40%）

**应用语义**：`slider01 = 0.7`，第二段强度 `(0.7−0.5)/0.5 = 0.4`；`headroomNitsForStrength(0.4) = 1 + 0.4×5 = 3.0`（动画/插值中可略低）。

| 项目 | 值 |
|------|-----|
| `settings get system screen_brightness` | 255 |
| `screen_brightness_mode` | 0（手动） |
| `Display Brightness` | ≈0.735 |
| `mBrightness` | ≈0.250 |
| `mUnthrottledBrightness` | 1.0 |
| `mHbmStatsState` | **HBM_ON_HDR** |
| `mHbmMode` | **hdr(0.7352586)**（与 `Display Brightness` 一致） |
| `mCachedBrightnessInfo.hbmMode` | 2 |
| `MainActivity` 窗口 | `sbrt=1.0` **`colorMode=COLOR_MODE_HDR`** **`desiredHdrHeadroom≈2.94`** |
| SurfaceFlinger | `numHdrLayers(1)`；主层 **`current/desired hdr/sdr ratio ≈ 2.94`**；**`dataspace=0x188a0000`**；日志中 **`desiredRatio` 约 2.55～2.94** |

**结论**：140% 下 headroom 目标约 **3.0**，系统窗口与合成层 **≈2.94×** 对齐；**`Display Brightness` / `mHbmMode` 读数约 0.74**，高于 120% 档（≈0.49），与第二段强度递增一致。

---

## 第四轮：亮度 **160%**（第二段约 60%）

**应用语义**：`slider01 = 0.8`，第二段强度 `(0.8−0.5)/0.5 = 0.6`；`headroomNitsForStrength(0.6) = 1 + 0.6×5 = 4.0`（动画中可略高）。

| 项目 | 值 |
|------|-----|
| `settings get system screen_brightness` | 255 |
| `screen_brightness_mode` | 0（手动） |
| `Display Brightness` | ≈0.750 |
| `mBrightness` | ≈0.250 |
| `mUnthrottledBrightness` | 1.0 |
| `mHbmStatsState` | **HBM_ON_HDR** |
| `mHbmMode` | **hdr(0.86241615)**（与 `Display Brightness` 数值不同字段，同为 HDR 路径读数） |
| `mCachedBrightnessInfo.hbmMode` | 2 |
| `MainActivity` 窗口 | `sbrt=1.0` **`colorMode=COLOR_MODE_HDR`** **`desiredHdrHeadroom≈4.05`** |
| SurfaceFlinger 主层 | **`desired hdr/sdr ratio ≈ 4.05`**，**`current hdr/sdr ratio = 3.0`**（**实际生效低于请求**，平台侧限幅）；**`dataspace=0x188a0000`** |
| HDR 事件 `desiredRatio` | 日志中约 **3.99～4.15**，末帧约 **4.05** |

**结论**：160% 下应用请求 headroom **≈4×** 与窗口 **≈4.05** 一致，但合成层 **`current` 被卡在 3.0**，**`desired` 为 4.05**——与此前在更高档位观察到的「请求高于实发」现象同型，属 **系统/OEM 对 hdr/sdr 比的上限策略**，非应用未发出请求。

---

## 第五轮：亮度 **180%**（第二段约 80%）

**应用语义**：`slider01 = 0.9`，第二段强度 `(0.9−0.5)/0.5 = 0.8`；`headroomNitsForStrength(0.8) = min(1 + 0.8×5, 6) = 5.0`（插值中可略低）。

| 项目 | 值 |
|------|-----|
| `settings get system screen_brightness` | 255 |
| `screen_brightness_mode` | 0（手动） |
| `Display Brightness` | ≈0.750 |
| `mBrightness` | ≈0.250 |
| `mUnthrottledBrightness` | 1.0 |
| `mHbmStatsState` | **HBM_ON_HDR** |
| `mHbmMode` | **hdr(0.9589035)** |
| `mCachedBrightnessInfo.hbmMode` | 2 |
| `MainActivity` 窗口 | `sbrt=1.0` **`colorMode=COLOR_MODE_HDR`** **`desiredHdrHeadroom≈4.95`** |
| SurfaceFlinger 主层 | **`desired hdr/sdr ratio ≈ 4.95`**，**`current hdr/sdr ratio = 3.0`**（仍与 160% 档相同上限）；**`dataspace=0x188a0000`** |
| HDR 事件 `desiredRatio` | 约 **4.70～4.95** |

**结论**：180% 下理论 headroom **5.0**，窗口与主层 **desired ≈4.95** 已接近；**`current` 仍为 3.0**，与 160% 相同，说明 **hdr/sdr 实发比在本机已达平台封顶**（再拉高滑块主要改变 **desired**，不改变 **current**）。**`Display Brightness` 读数与 160% 同为 ≈0.75**，亦与「实发比触顶」一致。

---

## 第六轮：亮度 **200%**（第二段满档 100%）

**应用语义**：`slider01 = 1.0`，第二段强度 **1.0**；`headroomNitsForStrength(1.0) = min(1 + 1.0×5, 6) = 6.0`（应用内**理论上限**）。

| 项目 | 值 |
|------|-----|
| `settings get system screen_brightness` | 255 |
| `screen_brightness_mode` | 0（手动） |
| `Display Brightness` | ≈0.750 |
| `mBrightness` | ≈0.250 |
| `mUnthrottledBrightness` | 1.0 |
| `mHbmStatsState` | **HBM_ON_HDR** |
| `mHbmMode` | **hdr(0.964264)** |
| `mCachedBrightnessInfo.hbmMode` | 2 |
| `MainActivity` 窗口 | `sbrt=1.0` **`colorMode=COLOR_MODE_HDR`** **`desiredHdrHeadroom≈5.03`**（向 **6.0** 插值过程中或受合成上限影响） |
| SurfaceFlinger 主层 | **`desired hdr/sdr ratio = 5.0`**，**`current hdr/sdr ratio = 3.0`**；**`dataspace=0x188a0000`** |
| HDR 事件 `desiredRatio` | 末帧 **`desiredRatio(5.00)`**（与主层 **desired=5** 一致） |

**结论**：满档时应用可向系统报到 **headroom 6.0**，但 **SurfaceFlinger 侧 `desired` 封顶为 5.0**（日志末帧 `desiredRatio(5.00)`），**`current` 仍为 3.0**，与 160% / 180% 相同——**实发 hdr/sdr 比在本机不因滑块从 180% 拉到 200% 而继续升高**。**`Display Brightness` 仍约 0.75**，与高档位平台一致。

---

## 综合解读：测试结果说明了什么

1. **第一段（≤100%）与第二段（>100%）分界有效**  
   100% 时无 HDR 窗口、无 headroom、**HBM_OFF**；自 120% 起进入 **`HBM_ON_HDR`**、扩展 **dataspace**，说明「中点左侧 / 右侧」在系统侧可区分。

2. **低档第二段（约 120%～140%）「跟手」最好**  
   此区间 **`desired` 与 `current` hdr/sdr 比一致**（约 2×、3×），观感增益与滑块大致成正比。

3. **约 160% 起出现「请求」与「实发」分叉**  
   `desired` 可升到约 **4～5×**，而 `current` **被卡在约 3.0×**；`Display Brightness` 约 **0.75** 后在 160%～200% **基本不再升高**。说明 **HyperOS / 面板策略对「实发 hdr/sdr 比」有硬顶**，应用侧继续拉高 headroom **主要抬高合成意图（desired），不一定抬高实发亮度**。

4. **满档 200% 的边界**  
   应用理论 headroom **6.0**，但 SurfaceFlinger 侧 `desired` **封顶约 5.0**，`current` 仍为 **3.0**——**滑块 180%→200% 对实发比几乎无增量**，更多是产品「余量」与动画终点，而非本机可再榨出的物理亮度。

5. **背光轴仍约 0.25 属预期**  
   **`mBrightness ≈ 0.25`** 在 HDR 档仍常见；户外阳光 HBM 仍受 **ALS / 策略** 约束，**不能**单靠应用等同「太阳下峰值 nit」。

*以上结论仅针对本次 **haotian** 样机；其它机型比例与封顶可能不同，但「desired 高于 current」在高端机上较常见。*

---

## 优化建议（产品 / 工程）

| 方向 | 建议 |
|------|------|
| **预期管理（产品）** | 在亮度说明或第二段副文案中写明：超过约 **150%～160%** 后，**部分机型**上增益可能**递减**（系统限制实发 hdr/sdr 比），避免用户认为「拉满 200% 一定比 160% 亮一倍」。 |
| **滑块映射（可选）** | 将第二段改为 **非线性曲线**：前半段（100%～约 140%）细粒度映射 headroom；**160% 以后**缩小步进增益或标注「精细微调」，使交互与 **有效增益区间** 对齐。 |
| **headroom 函数（工程）** | 将 `headroomNitsForStrength` 与实机观测对齐：例如 **合成层 `desired` 封顶约 5.0、`current` 约 3.0** 时，可将应用内请求 **收敛到 ≤5.0**（或与 SF 一致），减少「请求 6.0、系统只给到 5.0/3.0」的落差；或在注释中标明 **平台常见上限**。 |
| **像素侧（工程）** | 在 **160%～200%** 区间，若实发比不再增加，可略加强 **SdrPreviewTone** 的**观感调节**（对比度、中间调、高光滚降），使用户仍能感到「有变化」，但需控制与 **desired/current** 分叉带来的观感一致性。 |
| **无法由应用解决** | 阳光 **HBM**、环境光阈值、整机 **nit** 上限：仅能通过 **系统设置引导**（自动亮度、阳光模式等），不宜在应用内承诺可突破。 |

---

## 补充（重测）：强光照射 + **200%**

此前同场景一次抓取已按用户要求**作废**；以下为 **重测** 快照。

**测试条件（用户自述）**：**强光照射手机**，应用内亮度 **200%**（第二段满档）。设备 **`192.168.88.161:35061`（haotian）**。  
**抓取时间（PC 侧）**：**2026-03-25 22:09:17**（以执行 `adb` 的电脑时钟为准）。

| 项目 | 值 |
|------|-----|
| `screen_brightness` / `screen_brightness_mode` | **255** / **0**（手动） |
| `Display Brightness` | **≈0.750** |
| `mBrightness` | **≈0.250** |
| `mUnthrottledBrightness` | **1.0** |
| `mHbmStatsState` | **HBM_ON_HDR** |
| `mHbmMode` | **hdr(0.964264)** |
| `mCachedBrightnessInfo.adjustedBrightness` | **≈0.750** |
| **`mAmbientLux`（display 主读数）** | **≈344.19**，**`(old/invalid)`**；**`mIsInAllowedAmbientRange=false`** |
| **同 dump 另见** | **`mAmbientLuxValid=false`**；**`mAmbientLux: -1.0`**（字段与主读数并存，以系统实现为准） |
| **阳光 HBM 策略（配置项）** | **`mHbmData` / `HBM{minLux: 4475.0, ...}`**（传统「阳光激发」阈值仍很高，与 HDR 路径并行存在） |
| `MainActivity` 窗口 | `sbrt=1.0` **`COLOR_MODE_HDR`** **`desiredHdrHeadroom≈5.03`** |
| SurfaceFlinger 主层 | **`desired hdr/sdr ratio = 5.0`**，**`current hdr/sdr ratio = 3.0`**；**`dataspace=0x188a0000`** |
| HDR 事件 `desiredRatio`（日志尾部） | 末帧 **`desiredRatio(5.00)`** |
| **`adb shell dumpsys sensors`** | 本机返回 **`Can't find service: sensors`**；环境光需用 **`dumpsys sensorservice`**（见下节）。 |

**说明**：

- 重测与**室内第六轮（200%）**在 **亮度 / HDR 层 / 窗口 headroom** 上仍**同量级**；**强光并未在本轮 `dumpsys display` 中体现为更高的 `mAmbientLux` 或单独抬档**（读数仍异常/陈旧）。
- 若先生确在户外强光下，建议在 **手机保持解锁、HDR Viewer 前台 200%、手电筒勿遮挡感光孔** 时，由 PC **连续执行 3～5 次** `dumpsys display | findstr /i Ambient`，看是否出现跳变；或换 **USB 有线 adb** 减少无线延迟。
- **观感**仍可能亮于室内同读数；**adb 仅服务快照**，不能替代照度计。

---

## 补充：手电筒照射 + **亮度传感器原始事件**（`sensorservice`）

**背景**：用户反馈用手电筒照手机时**屏幕明显变亮**；此前仅看 `dumpsys display` 的 `mAmbientLux` **无法**反映该现象（常为陈旧/无效）。本机应使用 **`adb shell dumpsys sensorservice`**（服务名 **`sensorservice`**），**不是** `dumpsys sensors`。

**抓取时间（PC 侧）**：**2026-03-25 22:10 前后**（与下方 `wall=` 时间戳一致）。设备 **`192.168.88.161:35061`（haotian）**。应用侧仍为 **HDR Viewer、200%** 的测试上下文（与手电筒实验同步进行）。

### 环境光传感器（前摄旁 ALS）

- **传感器名称**：`TCS3720ALSPRX Ambient Light Sensor Non-wakeup`（`android.sensor.light`，handle **0x00000033**）。
- **缓冲末条事件**（`last 1000 events` 中序号 **997～1000**）：
  - **lux ≈ 111041.62**（`wall≈22:10:33`～**22:10:48**）
- **同缓冲较早样本**（室内/未直射）：约 **20～76 lux**（与常亮室内一致）。

**解读**：手电筒直射感光孔时，ALS **读数数量级跃迁**（→ **约 1.1×10⁵ lux** 量级），与「屏幕自动更亮」的**主观感受一致**；该值可能含 **饱和、非线性或厂商标定**，**不等于**物理照度计精确值，但足以解释 **AutomaticBrightness / 策略链路** 收到了「极亮环境」信号。

### 与 `dumpsys display` 的对照（同次会话、略晚一秒）

| 项目 | 值 |
|------|-----|
| `Display Brightness` | ≈0.750 |
| `mHbmStatsState` | HBM_ON_HDR |
| **`mAmbientLux`（display）** | 仍 **≈344 (old/invalid)**，`mAmbientLuxValid=false` |

**结论**：**SensorService 中的 ALS 事件流**与 **`DisplayManagerService` 对外 dump 的 `mAmbientLux`** 在本机上**不同步**；分析「手电筒是否让系统以为环境很亮」应**以 `dumpsys sensorservice` 中 Ambient Light 的最近事件为准**，而非仅看 `dumpsys display`。

---

## 总结论（从全文可推出的要点）

1. **应用两段位设计在系统侧成立**：≤100% 为 SDR 路径（无 HDR 窗口、无 headroom、**HBM_OFF**）；>100% 进入 **HDR + headroom + `HBM_ON_HDR`**，与滑块语义一致。  
2. **有效「跟手」增益主要在约 100%～140%**：此区间 **`desired` 与 `current` hdr/sdr 比一致**，观感与滑块大致成正比。  
3. **约 160% 起平台限幅**：**`desired`** 可到 **4～5×**，**`current` 约卡在 3.0×**；**160%～200%** 间 **`Display Brightness` 约 0.75 后基本不再升高**——**再拉高滑块主要抬高请求值，实发 hdr/sdr 比未必再涨**。  
4. **满档 200%**：应用可报到 headroom **6.0**，SurfaceFlinger **`desired` 封顶约 5.0**，**`current` 仍为 3.0**——**180%→200% 对实发比几乎无增量**（本机 haotian 样机）。  
5. **背光读数 `mBrightness≈0.25` 与 HDR 路径并存**属常见现象；整机极限仍受 **OEM 策略** 约束。  
6. **环境光判断勿只看 `dumpsys display`**：`mAmbientLux` 常 **陈旧/无效**；**手电筒照射时屏幕变亮**与 **`dumpsys sensorservice` 中 ALS 事件骤增（如达 ~1.1×10⁵ lux 量级）**一致，说明 **系统确实收到了「极亮」信号**；该数值为传感器/策略侧读数，**不必**当作精确物理照度。  
7. **优化方向**（见上文「优化建议」表）：管理用户对 **160% 以上** 的预期、可选非线性滑块、headroom 与平台封顶对齐、高段用像素观感补偿；**阳光 HBM 等**仍以系统设置引导为主。

---

*记录整理自会话中 ADB 抓取；若需复测，请在同机、同应用版本下保持前台预览页再执行 `dumpsys`。*
