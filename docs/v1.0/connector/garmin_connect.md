# Garmin Connect 连接器说明

## 1. 文档范围

本文档只描述 Garmin Connect 数据落入 raw 库之后的当前状态，口径严格限定为：

1. 当前会写入哪些 raw 表。
2. raw 表本身有哪些字段。
3. `payload_jsonb` 中原始响应数据的字段清单。

说明：

1. 本文档不描述 staging、intermediate、marts 层的派生字段。
2. `payload_jsonb` 字段清单按“当前项目代码 + 当前适配库公开结构 + 当前已知样例”整理。
3. 若某字段语义不能完全确认，文档会明确标注“含义待确认”或“结构待确认”。

## 2. 当前数据集与 raw 表映射

当前 Garmin Connect 连接器会抓取并落库以下 5 类数据：

| 数据集 | raw 表 | `source_stream` | 数据粒度 | 说明 |
|---|---|---|---|---|
| 用户资料 | `raw.health_snapshot_record` | `profile` | 用户级当前快照 | 当前用户资料与平台标识，不支持按历史日期查询 |
| 每日汇总 | `raw.health_snapshot_record` | `daily_summary` | 用户-日期 | 每日步数、距离、热量、活跃分钟等汇总 |
| 睡眠 | `raw.health_snapshot_record` | `sleep` | 用户-日期 / 睡眠会话 | 每日睡眠汇总与阶段明细 |
| 活动 | `raw.health_event_record` | `activity` | 活动会话 | 跑步、骑行等活动记录 |
| 心率 | `raw.health_timeseries_record` | `heart_rate` | 用户-日期 | 日级心率汇总与采样序列 |

## 3. `raw.health_snapshot_record`

`raw.health_snapshot_record` 当前承载 3 类 Garmin 数据：

1. `profile`
2. `daily_summary`
3. `sleep`

### 3.1 表字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `UUID` | raw 记录主键 |
| `account_id` | `UUID` | 平台账号 ID |
| `connector_config_id` | `UUID` | Garmin 连接器配置实例 ID |
| `sync_task_id` | `UUID` | 本次写入对应的同步任务 ID |
| `connector_id` | `VARCHAR(120)` | 当前固定为 `garmin-connect` |
| `source_stream` | `VARCHAR(120)` | 当前在本表中取值为 `profile`、`daily_summary`、`sleep` |
| `external_id` | `VARCHAR(255)` | 来源记录稳定标识 |
| `source_record_date` | `DATE` | 来源记录所属日期 |
| `source_record_at` | `TIMESTAMPTZ` | 来源记录的主要业务时间点 |
| `source_updated_at` | `TIMESTAMPTZ` | 来源记录更新时间或结束时间 |
| `payload_hash` | `VARCHAR(128)` | `payload_jsonb` 内容哈希 |
| `collected_at` | `TIMESTAMPTZ` | 同步任务实际采集入库时间 |
| `payload_jsonb` | `JSONB` | Garmin 原始响应 JSON |
| `created_at` | `TIMESTAMPTZ` | 记录创建时间 |
| `updated_at` | `TIMESTAMPTZ` | 记录最后更新时间 |

### 3.2 `source_stream=profile`

当前 `profile` 表示“当前用户资料快照”，不是按日期查询的历史资料。  
`source_record_date` 仅表示本次抓取快照的采集日期，不表示 Garmin 侧支持历史 profile 回溯。

#### 外层 raw 字段

| 字段 | 取值规则 |
|---|---|
| `source_stream` | 固定为 `profile` |
| `external_id` | `payload.externalId`，否则 `payload.profileId`，否则 `payload.id`，否则 `payload.garminGUID`，否则用户名 |
| `source_record_date` | 当前应用本地时区当天日期，表示快照采集日期 |
| `source_record_at` | `NULL` |
| `source_updated_at` | 当前实现通常为 `NULL` |

#### `payload_jsonb` 字段清单

`payload_jsonb` 保存当前用户资料接口返回的原始响应。字段是否一定返回，依 Garmin 账号、隐私设置和接口版本而定。

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `id` | `string / number` | 资料记录 ID |
| `profileId` | `string / number` | Profile 主键或档案 ID |
| `connectionRequestId` | `string / number` | 连接请求 ID，含义待确认 |
| `garminGUID` | `string` | Garmin 全局标识 |
| `externalId` | `string / number` | 外部用户标识，可用于生成 `external_id` |
| `displayName` | `string` | 展示名 |
| `fullName` | `string` | 全名 |
| `userName` | `string` | 用户名 |
| `userProfileFullName` | `string` | 用户资料全名，和 `fullName` 的区别待确认 |
| `profileImageType` | `string` | 头像类型 |
| `profileImageUuid` | `string` | 头像 UUID |
| `profileImageUrlLarge` | `string` | 大图头像 URL |
| `profileImageUrlMedium` | `string` | 中图头像 URL |
| `profileImageUrlSmall` | `string` | 小图头像 URL |
| `location` | `string` | 位置描述 |
| `facebookUrl` | `string` | Facebook 链接 |
| `twitterUrl` | `string` | Twitter 链接 |
| `personalWebsite` | `string` | 个人网站 |
| `motivation` | `string` | 个性签名或动机说明 |
| `bio` | `string` | 个人简介 |
| `primaryActivity` | `string` | 主活动类型 |
| `otherPrimaryActivity` | `string` | 其他主活动类型说明 |
| `favoriteActivityTypes` | `array` | 喜好活动类型列表 |
| `favoriteCyclingActivityTypes` | `array` | 喜好骑行活动类型列表 |
| `runningTrainingSpeed` | `number / string` | 跑步训练速度，单位含义待确认 |
| `cyclingTrainingSpeed` | `number / string` | 骑行训练速度，单位含义待确认 |
| `swimmingTrainingSpeed` | `number / string` | 游泳训练速度，单位含义待确认 |
| `cyclingClassification` | `string` | 骑行分类，含义待确认 |
| `cyclingMaxAvgPower` | `number` | 骑行最大平均功率，单位通常为瓦 |
| `otherActivity` | `string` | 其他活动说明 |
| `otherMotivation` | `string` | 其他动机说明 |
| `profileVisibility` | `string / number` | 资料可见性设置 |
| `activityStartVisibility` | `string / number` | 活动开始位置可见性 |
| `activityMapVisibility` | `string / number` | 活动轨迹地图可见性 |
| `courseVisibility` | `string / number` | 课程/路线可见性 |
| `activityHeartRateVisibility` | `string / number` | 活动心率可见性 |
| `activityPowerVisibility` | `string / number` | 活动功率可见性 |
| `badgeVisibility` | `string / number` | 徽章可见性 |
| `showAge` | `boolean` | 是否展示年龄 |
| `showWeight` | `boolean` | 是否展示体重 |
| `showHeight` | `boolean` | 是否展示身高 |
| `showWeightClass` | `boolean` | 是否展示体重等级 |
| `showAgeRange` | `boolean` | 是否展示年龄区间 |
| `showGender` | `boolean` | 是否展示性别 |
| `showActivityClass` | `boolean` | 是否展示活动等级 |
| `showVO2Max` | `boolean` | 是否展示 VO2Max |
| `showPersonalRecords` | `boolean` | 是否展示个人纪录 |
| `showLast12Months` | `boolean` | 是否展示最近 12 个月统计 |
| `showLifetimeTotals` | `boolean` | 是否展示生涯累计统计 |
| `showUpcomingEvents` | `boolean` | 是否展示即将开始的事件 |
| `showRecentFavorites` | `boolean` | 是否展示最近收藏 |
| `showRecentDevice` | `boolean` | 是否展示最近设备 |
| `showRecentGear` | `boolean` | 是否展示最近装备 |
| `showBadges` | `boolean` | 是否展示徽章 |
| `userRoles` | `array` | 用户角色列表 |
| `nameApproved` | `boolean` | 名称是否通过审核，含义待确认 |
| `makeGolfScorecardsPrivate` | `boolean` | 高尔夫记分卡是否私有 |
| `allowGolfLiveScoring` | `boolean` | 是否允许高尔夫实时计分 |
| `allowGolfScoringByConnections` | `boolean` | 是否允许联系人代为高尔夫计分 |
| `userLevel` | `number` | 用户等级 |
| `userPoint` | `number` | 用户积分 |
| `levelUpdateDate` | `string / number` | 等级更新时间 |
| `levelIsViewed` | `boolean` | 当前等级更新是否已查看 |
| `levelPointThreshold` | `number` | 当前等级阈值积分 |
| `userPointOffset` | `number` | 用户积分偏移，含义待确认 |
| `userPro` | `boolean` | 是否为高级/Pro 用户，含义待确认 |
| `provider` | `string` | 平台标识；若存在，通常表示来源平台 |
| `region` | `string` | 区域标识；若存在，通常表示地区 |

### 3.3 `source_stream=daily_summary`

#### 外层 raw 字段

| 字段 | 取值规则 |
|---|---|
| `source_stream` | 固定为 `daily_summary` |
| `external_id` | `{username}:{date}` |
| `source_record_date` | 被抓取的自然日 |
| `source_record_at` | `NULL` |
| `source_updated_at` | 当前实现通常为 `NULL` |

#### `payload_jsonb` 字段清单

下表列出当前已知的 `daily_summary` 原始字段。

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `userProfileId` | `string / number` | 用户资料 ID |
| `userDailySummaryId` | `string / number` | 每日汇总主键 |
| `calendarDate` | `string` | 汇总所属日期 |
| `uuid` | `string` | 记录 UUID |
| `source` | `string` | 数据来源标识 |
| `totalKilocalories` | `number` | 总热量消耗，单位千卡 |
| `activeKilocalories` | `number` | 活动热量，单位千卡 |
| `bmrKilocalories` | `number` | 基础代谢热量，单位千卡 |
| `wellnessKilocalories` | `number` | Wellness 热量，含义待确认 |
| `burnedKilocalories` | `number` | 已消耗热量 |
| `consumedKilocalories` | `number` | 已摄入热量 |
| `remainingKilocalories` | `number` | 剩余热量 |
| `netCalorieGoal` | `number` | 净热量目标 |
| `netRemainingKilocalories` | `number` | 剩余净热量 |
| `totalSteps` | `number` | 当日总步数 |
| `dailyStepGoal` | `number` | 当日步数目标 |
| `totalDistanceMeters` | `number` | 总距离，单位米 |
| `wellnessDistanceMeters` | `number` | Wellness 距离，单位米，含义待确认 |
| `wellnessActiveKilocalories` | `number` | Wellness 活动热量，含义待确认 |
| `wellnessStartTimeGmt` | `string / number` | Wellness 窗口开始时间（GMT） |
| `wellnessEndTimeGmt` | `string / number` | Wellness 窗口结束时间（GMT） |
| `wellnessStartTimeLocal` | `string / number` | Wellness 窗口开始时间（本地） |
| `wellnessEndTimeLocal` | `string / number` | Wellness 窗口结束时间（本地） |
| `durationInMilliseconds` | `number` | 汇总窗口时长，单位毫秒 |
| `wellnessDescription` | `string` | Wellness 描述，含义待确认 |
| `highlyActiveSeconds` | `number` | 高强度活动秒数 |
| `activeSeconds` | `number` | 活动秒数 |
| `sedentarySeconds` | `number` | 久坐秒数 |
| `sleepingSeconds` | `number` | 睡眠秒数 |
| `includesWellnessData` | `boolean` | 是否包含 wellness 数据 |
| `includesActivityData` | `boolean` | 是否包含活动数据 |
| `includesCalorieConsumedData` | `boolean` | 是否包含摄入热量数据 |
| `privacyProtected` | `boolean` | 是否受隐私保护 |
| `moderateIntensityMinutes` | `number` | 中等强度分钟数 |
| `vigorousIntensityMinutes` | `number` | 高强度分钟数 |
| `intensityMinutesGoal` | `number` | 强度分钟目标 |
| `floorsAscendedInMeters` | `number` | 累计上升高度，单位米 |
| `floorsDescendedInMeters` | `number` | 累计下降高度，单位米 |
| `floorsAscended` | `number` | 上楼层数 |
| `floorsDescended` | `number` | 下楼层数 |
| `userFloorsAscendedGoal` | `number` | 上楼目标层数 |
| `minHeartRate` | `number` | 当日最小心率 |
| `maxHeartRate` | `number` | 当日最大心率 |
| `restingHeartRate` | `number` | 静息心率 |
| `lastSevenDaysAvgRestingHeartRate` | `number` | 最近 7 天平均静息心率 |
| `averageStressLevel` | `number` | 平均压力值 |
| `maxStressLevel` | `number` | 最大压力值 |
| `stressDuration` | `number` | 压力持续时间 |
| `restStressDuration` | `number` | 静息压力持续时间 |
| `activityStressDuration` | `number` | 活动压力持续时间 |
| `uncategorizedStressDuration` | `number` | 未分类压力持续时间 |
| `totalStressDuration` | `number` | 总压力持续时间 |
| `lowStressDuration` | `number` | 低压力持续时间 |
| `mediumStressDuration` | `number` | 中压力持续时间 |
| `highStressDuration` | `number` | 高压力持续时间 |
| `stressQualifier` | `string / number` | 压力分级标识，含义待确认 |
| `measurableAwakeDuration` | `number` | 可测清醒时长 |
| `measurableAsleepDuration` | `number` | 可测睡眠时长 |
| `lastSyncTimestampGMT` | `string / number` | 最后同步时间 |
| `minAvgHeartRate` | `number` | 平均心率的最小值，含义待确认 |
| `maxAvgHeartRate` | `number` | 平均心率的最大值，含义待确认 |

### 3.4 `source_stream=sleep`

#### 外层 raw 字段

| 字段 | 取值规则 |
|---|---|
| `source_stream` | 固定为 `sleep` |
| `external_id` | `dailySleepDTO.id`，否则 `sleep:{username}:{date}` |
| `source_record_date` | `dailySleepDTO.calendarDate`，否则被抓取日期 |
| `source_record_at` | `dailySleepDTO.sleepStartTimestampGMT` 解析后的时间 |
| `source_updated_at` | `dailySleepDTO.sleepEndTimestampGMT` 解析后的时间 |

#### `payload_jsonb` 顶层字段清单

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `dailySleepDTO` | `object` | 睡眠主对象 |
| `remSleepData` | `array` | REM 睡眠阶段数组 |
| `sleepMovement` | `array` | 睡眠动作数组 |
| `sleepLevels` | `array` | 睡眠阶段数组 |
| `sleepRestlessMoments` | `array` | 睡眠不安/躁动时刻数组 |
| `restlessMomentsCount` | `number` | 不安时刻数量 |
| `wellnessSpO2SleepSummaryDTO` | `object` | 睡眠期血氧汇总对象，内部结构待确认 |
| `wellnessEpochSPO2DataDTOList` | `array` | 睡眠期逐时段血氧数组，内部结构待确认 |
| `wellnessEpochRespirationDataDTOList` | `array` | 睡眠期逐时段呼吸数组 |
| `sleepHeartRate` | `array` | 睡眠期心率数组 |
| `sleepStress` | `array` | 睡眠期压力数组 |
| `sleepBodyBattery` | `array` | 睡眠期 Body Battery 数组 |
| `hrvData` | `array` | 睡眠期 HRV 数组 |
| `skinTempDataExists` | `boolean` | 是否存在皮温数据 |
| `avgOvernightHrv` | `number` | 夜间平均 HRV |
| `hrvStatus` | `string / number` | HRV 状态，含义待确认 |
| `bodyBatteryChange` | `number` | 睡眠期间 Body Battery 变化量 |
| `restingHeartRate` | `number` | 睡眠相关静息心率 |

#### `dailySleepDTO` 字段清单

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `dailySleepDTO.id` | `string / number` | 睡眠记录 ID |
| `dailySleepDTO.userProfilePK` | `string / number` | 用户资料主键 |
| `dailySleepDTO.calendarDate` | `string` | 睡眠归属日期 |
| `dailySleepDTO.sleepTimeSeconds` | `number` | 总睡眠时长，单位秒 |
| `dailySleepDTO.napTimeSeconds` | `number` | 小睡时长，单位秒 |
| `dailySleepDTO.sleepWindowConfirmed` | `boolean` | 睡眠窗口是否已确认 |
| `dailySleepDTO.sleepWindowConfirmationType` | `string / number` | 睡眠窗口确认类型，含义待确认 |
| `dailySleepDTO.sleepStartTimestampGMT` | `string / number` | 睡眠开始时间（GMT） |
| `dailySleepDTO.sleepEndTimestampGMT` | `string / number` | 睡眠结束时间（GMT） |
| `dailySleepDTO.sleepStartTimestampLocal` | `string / number` | 睡眠开始时间（本地） |
| `dailySleepDTO.sleepEndTimestampLocal` | `string / number` | 睡眠结束时间（本地） |
| `dailySleepDTO.autoSleepStartTimestampGMT` | `string / number` | 自动识别的睡眠开始时间（GMT） |
| `dailySleepDTO.autoSleepEndTimestampGMT` | `string / number` | 自动识别的睡眠结束时间（GMT） |
| `dailySleepDTO.sleepQualityTypePK` | `string / number` | 睡眠质量类型主键，含义待确认 |
| `dailySleepDTO.sleepResultTypePK` | `string / number` | 睡眠结果类型主键，含义待确认 |
| `dailySleepDTO.unmeasurableSleepSeconds` | `number` | 不可测睡眠时长 |
| `dailySleepDTO.deepSleepSeconds` | `number` | 深睡时长 |
| `dailySleepDTO.lightSleepSeconds` | `number` | 浅睡时长 |
| `dailySleepDTO.remSleepSeconds` | `number` | REM 时长 |
| `dailySleepDTO.awakeSleepSeconds` | `number` | 清醒时长 |
| `dailySleepDTO.deviceRemCapable` | `boolean` | 设备是否支持 REM 检测 |
| `dailySleepDTO.retro` | `boolean` | 含义待确认 |
| `dailySleepDTO.sleepFromDevice` | `boolean` | 是否由设备侧直接产生 |
| `dailySleepDTO.averageRespirationValue` | `number` | 平均呼吸值 |
| `dailySleepDTO.lowestRespirationValue` | `number` | 最低呼吸值 |
| `dailySleepDTO.highestRespirationValue` | `number` | 最高呼吸值 |
| `dailySleepDTO.awakeCount` | `number` | 清醒次数 |
| `dailySleepDTO.avgSleepStress` | `number` | 平均睡眠压力值 |
| `dailySleepDTO.ageGroup` | `string / number` | 年龄组，含义待确认 |
| `dailySleepDTO.sleepScoreFeedback` | `string` | 睡眠评分反馈文案 |
| `dailySleepDTO.sleepScoreInsight` | `string` | 睡眠评分洞察文案 |
| `dailySleepDTO.sleepScorePersonalizedInsight` | `string` | 个性化睡眠洞察文案 |
| `dailySleepDTO.sleepScores` | `object` | 睡眠评分对象 |
| `dailySleepDTO.sleepVersion` | `string / number` | 睡眠算法版本，含义待确认 |
| `dailySleepDTO.sleepNeed` | `object` | 当前睡眠需求对象 |
| `dailySleepDTO.nextSleepNeed` | `object` | 下一次睡眠需求对象 |

#### `dailySleepDTO.sleepScores` 字段清单

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `dailySleepDTO.sleepScores.totalDuration` | `object` | 总时长评分对象 |
| `dailySleepDTO.sleepScores.stress` | `object` | 压力评分对象 |
| `dailySleepDTO.sleepScores.awakeCount` | `object` | 清醒次数评分对象 |
| `dailySleepDTO.sleepScores.overall` | `object` | 综合评分对象 |
| `dailySleepDTO.sleepScores.remPercentage` | `object` | REM 占比评分对象 |
| `dailySleepDTO.sleepScores.restlessness` | `object` | 躁动程度评分对象 |
| `dailySleepDTO.sleepScores.lightPercentage` | `object` | 浅睡占比评分对象 |
| `dailySleepDTO.sleepScores.deepPercentage` | `object` | 深睡占比评分对象 |

#### 评分对象字段清单

以下字段适用于 `sleepScores` 下的单个评分对象。不同评分对象实际字段可能略有差异。

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `*.value` | `number` | 当前评分值 |
| `*.qualifierKey` | `string` | 评分等级标识 |
| `*.idealStart` | `number` | 理想区间开始值 |
| `*.idealEnd` | `number` | 理想区间结束值 |
| `*.optimalStart` | `number` | 最优区间开始值 |
| `*.optimalEnd` | `number` | 最优区间结束值 |

#### `dailySleepDTO.sleepNeed` / `dailySleepDTO.nextSleepNeed` 字段清单

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `*.userProfilePk` | `string / number` | 用户资料主键 |
| `*.calendarDate` | `string` | 生效日期 |
| `*.deviceId` | `string / number` | 设备 ID |
| `*.timestampGmt` | `string / number` | 生成时间（GMT） |
| `*.baseline` | `number` | 基线睡眠需求 |
| `*.actual` | `number` | 实际睡眠需求 |
| `*.feedback` | `string` | 睡眠需求反馈文案 |
| `*.trainingFeedback` | `string` | 训练反馈文案 |
| `*.sleepHistoryAdjustment` | `number` | 睡眠历史调整值 |
| `*.hrvAdjustment` | `number` | HRV 调整值 |
| `*.napAdjustment` | `number` | 小睡调整值 |
| `*.displayedForTheDay` | `boolean` | 是否已对当天展示 |
| `*.preferredActivityTracker` | `string / number` | 首选活动追踪设备，含义待确认 |

#### 时序数组字段清单

以下数组当前原样保存在 `payload_jsonb` 中。

##### `remSleepData[*]` / `sleepLevels[*]`

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `*.startGMT` | `string / number` | 阶段开始时间 |
| `*.endGMT` | `string / number` | 阶段结束时间 |
| `*.activityLevel` | `string / number` | 阶段级别，具体枚举含义待确认 |

##### `sleepMovement[*]` / `sleepRestlessMoments[*]` / `sleepHeartRate[*]` / `sleepStress[*]` / `sleepBodyBattery[*]` / `hrvData[*]`

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `*.startGMT` | `string / number` | 采样开始时间 |
| `*.value` | `number / string` | 采样值；不同数组代表不同指标 |

##### `wellnessEpochRespirationDataDTOList[*]`

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `*.startTimeGMT` | `string / number` | 呼吸采样开始时间 |
| `*.respirationValue` | `number` | 呼吸值 |

##### `wellnessSpO2SleepSummaryDTO`

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `wellnessSpO2SleepSummaryDTO` | `object` | 血氧睡眠汇总对象；当前项目未解析其内部字段，结构待确认 |

##### `wellnessEpochSPO2DataDTOList[*]`

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `wellnessEpochSPO2DataDTOList[*]` | `object` | 睡眠期血氧明细对象；当前项目未解析其内部字段，结构待确认 |

## 4. `raw.health_event_record`

`raw.health_event_record` 当前承载 1 类 Garmin 数据：

1. `activity`

### 4.1 表字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `UUID` | raw 记录主键 |
| `account_id` | `UUID` | 平台账号 ID |
| `connector_config_id` | `UUID` | 连接器配置实例 ID |
| `sync_task_id` | `UUID` | 本次写入对应的同步任务 ID |
| `connector_id` | `VARCHAR(120)` | 当前固定为 `garmin-connect` |
| `source_stream` | `VARCHAR(120)` | 当前固定为 `activity` |
| `external_id` | `VARCHAR(255)` | 活动稳定标识 |
| `source_record_date` | `DATE` | 活动所属日期，按活动开始时间折算到本地日期 |
| `source_record_at` | `TIMESTAMPTZ` | 活动开始时间 |
| `source_updated_at` | `TIMESTAMPTZ` | 活动更新时间 |
| `payload_hash` | `VARCHAR(128)` | `payload_jsonb` 内容哈希 |
| `collected_at` | `TIMESTAMPTZ` | 同步采集入库时间 |
| `payload_jsonb` | `JSONB` | Garmin 活动原始响应 JSON |
| `created_at` | `TIMESTAMPTZ` | 记录创建时间 |
| `updated_at` | `TIMESTAMPTZ` | 记录最后更新时间 |

### 4.2 `source_stream=activity`

#### 外层 raw 字段

| 字段 | 取值规则 |
|---|---|
| `source_stream` | 固定为 `activity` |
| `external_id` | `activityId`，否则 `activityUUID`，否则 `summaryId`，否则项目内回退值 |
| `source_record_date` | 活动开始时间转换后的本地日期 |
| `source_record_at` | `startTimeGMT` 或 `startTimeLocal` 解析后的时间 |
| `source_updated_at` | `updateDate` 解析后的时间 |

#### `payload_jsonb` 字段清单

下表列出当前已知的活动原始字段。

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `activityId` | `string / number` | 活动 ID |
| `activityUUID` | `string` | 活动 UUID |
| `summaryId` | `string / number` | 活动摘要 ID |
| `ownerId` | `string / number` | 所属用户 ID |
| `deviceId` | `string / number` | 设备 ID |
| `activityName` | `string` | 活动名称 |
| `description` | `string` | 活动描述 |
| `eventType` | `string` | 事件类型或活动名称回退字段 |
| `activityType` | `object / string` | 活动类型对象或字符串 |
| `activityType.typeId` | `number` | 活动类型 ID |
| `activityType.typeKey` | `string` | 活动类型编码，如 `running` |
| `activityType.typeName` | `string` | 活动类型名称 |
| `activityType.parentTypeId` | `number` | 父级活动类型 ID |
| `activityType.sortOrder` | `number` | 排序号 |
| `activityType.isHidden` | `boolean` | 是否隐藏 |
| `activityType.restricted` | `boolean` | 是否受限 |
| `activityType.trimmable` | `boolean` | 是否允许裁剪，含义待确认 |
| `startTimeLocal` | `string / number` | 活动开始时间（本地） |
| `startTimeGMT` | `string / number` | 活动开始时间（GMT） |
| `updateDate` | `string / number` | 活动更新时间 |
| `startLatitude` | `number` | 起点纬度 |
| `startLongitude` | `number` | 起点经度 |
| `endLatitude` | `number` | 终点纬度 |
| `endLongitude` | `number` | 终点经度 |
| `distance` | `number` | 活动距离，单位米 |
| `duration` | `number` | 活动持续时长，单位秒 |
| `movingDuration` | `number` | 移动时长，单位秒 |
| `elapsedDuration` | `number` | 总耗时，单位秒 |
| `elevationGain` | `number` | 爬升，单位米 |
| `elevationLoss` | `number` | 下降，单位米 |
| `maxElevation` | `number` | 最高海拔 |
| `minElevation` | `number` | 最低海拔 |
| `averageSpeed` | `number` | 平均速度 |
| `averageMovingSpeed` | `number` | 平均移动速度 |
| `maxSpeed` | `number` | 最大速度 |
| `calories` | `number` | 总热量，单位千卡 |
| `bmrCalories` | `number` | 基础代谢热量，单位千卡 |
| `averageHR` | `number` | 平均心率 |
| `averageHeartRate` | `number` | 平均心率回退字段 |
| `maxHR` | `number` | 最大心率 |
| `maxHeartRate` | `number` | 最大心率回退字段 |
| `averageRunCadence` | `number` | 平均跑步步频 |
| `maxRunCadence` | `number` | 最大跑步步频 |
| `averagePower` | `number` | 平均功率 |
| `maxPower` | `number` | 最大功率 |
| `minPower` | `number` | 最小功率 |
| `normalizedPower` | `number` | 标准化功率 |
| `totalWork` | `number` | 总做功 |
| `groundContactTime` | `number` | 触地时间 |
| `strideLength` | `number` | 步幅 |
| `verticalOscillation` | `number` | 垂直振幅 |
| `verticalRatio` | `number` | 垂直比 |
| `trainingEffect` | `number` | 训练效果 |
| `anaerobicTrainingEffect` | `number` | 无氧训练效果 |
| `aerobicTrainingEffectMessage` | `string` | 有氧训练效果文案 |
| `anaerobicTrainingEffectMessage` | `string` | 无氧训练效果文案 |
| `trainingEffectLabel` | `string` | 训练效果标签 |
| `activityTrainingLoad` | `number` | 活动训练负荷 |
| `minActivityLapDuration` | `number` | 最短圈时长 |
| `directWorkoutFeel` | `string / number` | 主观训练感受，含义待确认 |
| `directWorkoutRpe` | `string / number` | 主观用力等级 RPE |
| `moderateIntensityMinutes` | `number` | 中等强度分钟数 |
| `vigorousIntensityMinutes` | `number` | 高强度分钟数 |
| `steps` | `number` | 活动步数 |
| `recoveryHeartRate` | `number` | 恢复心率 |
| `avgGradeAdjustedSpeed` | `number` | 坡度修正平均速度 |
| `differenceBodyBattery` | `number` | Body Battery 变化量 |
| `maxVerticalSpeed` | `number` | 最大垂直速度 |
| `waterEstimated` | `number / boolean` | 估算水量，含义待确认 |

## 5. `raw.health_timeseries_record`

`raw.health_timeseries_record` 当前承载 1 类 Garmin 数据：

1. `heart_rate`

### 5.1 表字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `UUID` | raw 记录主键 |
| `account_id` | `UUID` | 平台账号 ID |
| `connector_config_id` | `UUID` | 连接器配置实例 ID |
| `sync_task_id` | `UUID` | 本次写入对应的同步任务 ID |
| `connector_id` | `VARCHAR(120)` | 当前固定为 `garmin-connect` |
| `source_stream` | `VARCHAR(120)` | 当前固定为 `heart_rate` |
| `external_id` | `VARCHAR(255)` | 日级心率记录稳定标识 |
| `source_record_date` | `DATE` | 心率数据所属日期 |
| `source_record_at` | `TIMESTAMPTZ` | 心率窗口开始时间 |
| `source_updated_at` | `TIMESTAMPTZ` | 心率窗口结束时间 |
| `payload_hash` | `VARCHAR(128)` | `payload_jsonb` 内容哈希 |
| `collected_at` | `TIMESTAMPTZ` | 同步采集入库时间 |
| `payload_jsonb` | `JSONB` | Garmin 心率原始响应 JSON |
| `created_at` | `TIMESTAMPTZ` | 记录创建时间 |
| `updated_at` | `TIMESTAMPTZ` | 记录最后更新时间 |

### 5.2 `source_stream=heart_rate`

#### 外层 raw 字段

| 字段 | 取值规则 |
|---|---|
| `source_stream` | 固定为 `heart_rate` |
| `external_id` | `hr:{username}:{date}` |
| `source_record_date` | `calendarDate`，否则被抓取日期 |
| `source_record_at` | `startTimestampGMT` 解析后的时间 |
| `source_updated_at` | `endTimestampGMT` 解析后的时间 |

#### `payload_jsonb` 字段清单

下表列出当前已知的心率原始字段。

| 字段路径 | 类型 | 说明 |
|---|---|---|
| `userProfilePK` | `string / number` | 用户资料主键 |
| `calendarDate` | `string` | 心率数据所属日期 |
| `startTimestampGMT` | `string / number` | 窗口开始时间（GMT） |
| `endTimestampGMT` | `string / number` | 窗口结束时间（GMT） |
| `startTimestampLocal` | `string / number` | 窗口开始时间（本地） |
| `endTimestampLocal` | `string / number` | 窗口结束时间（本地） |
| `restingHeartRate` | `number` | 静息心率 |
| `minHeartRate` | `number` | 当日最小心率 |
| `maxHeartRate` | `number` | 当日最大心率 |
| `lastSevenDaysAvgRestingHeartRate` | `number` | 最近 7 天平均静息心率 |
| `averageHeartRate` | `number` | 平均心率；若存在则直接来自原始响应 |
| `sampleCount` | `number` | 样本点数量；若存在则直接来自原始响应 |
| `heartRateValueDescriptors` | `array` | 心率采样数组列定义 |
| `heartRateValueDescriptors[*].index` | `number` | 列下标 |
| `heartRateValueDescriptors[*].key` | `string` | 列名，常见如 `timestamp`、`heartrate` |
| `heartRateValues` | `array` | 心率采样值数组 |
| `heartRateValues[*]` | `array` | 单个采样点数组，具体列含义以 `heartRateValueDescriptors` 为准 |

## 6. 当前 raw 写入规则

### 6.1 唯一键

3 张 raw 表统一使用以下唯一键：

`(account_id, connector_config_id, source_stream, source_record_date, external_id)`

### 6.2 写入与更新规则

1. 唯一键不存在时插入新记录。
2. 唯一键存在且 `payload_hash` 相同，视为重复数据，不更新。
3. 唯一键存在且 `payload_hash` 不同，更新当前记录的 `payload_jsonb` 和相关元字段。

## 7. 说明

1. 本文档中的 `payload_jsonb` 字段清单按当前项目已知结构尽量列全。
2. 对语义不能完全确认的字段，文档已显式标注“含义待确认”或“结构待确认”。
3. 若未来发现新的 Garmin 原始字段进入 `payload_jsonb`，应继续在本文件中补充。
