# 爱吃白饭的大肥鱼 · 手机版

**装在手机上自己跑的 AI 伴侣，不需要电脑，不需要网桥，只需要一个 DeepSeek API Key。**

她叫「张小满」，一条圆滚滚的胖鱼。会聊天、会记事、会生气、会长记性。

---

## 这是什么

一个纯 Android 原生 App（无 Gradle、无第三方库），直接调 DeepSeek API。
你的聊天记录、记忆、人格全部存在手机本地，不上传任何服务器。

| | 电脑版 | 手机版（这个） |
|---|---|---|
| 需要电脑 | 是（跑网桥） | **不需要** |
| 需要 WiFi | 是 | **不需要** |
| 需要配置 | IP + 端口 + 口令 | **只填一个 API Key** |
| 数据存哪 | 电脑 | **手机本地** |

---

## 功能

### 已经有的

- **聊天** —— 直接调 DeepSeek，流式回复（一个字一个字出）
- **记忆** —— 五轨分层（记忆/用户/关键/项目/日记），带权重和遗忘曲线
- **人格** —— Markdown 文件，随便改，支持导入导出
- **历史** —— 本地保存，自动备份到外部目录
- **表情包** —— 内置 + 自己加，从相册导入
- **聊天背景** —— 11 张内置 + 相册自选
- **气泡皮肤** —— 6 套配色
- **深浅主题** —— 深蓝 / 浅色
- **定时提醒** —— 到点弹通知
- **状态栏** —— 显示记忆条数、历史条数，可开关

### 还在做

- 情绪系统（九德十轴）
- 语音
- 发图

---

## 自己编译

**不需要 Android Studio，不需要 Gradle。** 只需要：

```
Android SDK build-tools 34.0.0
JDK 17
Python 3（用于 dex 插入）
```

### 构建

```bash
cd android
bash build.sh
```

产物在 `android/build/大肥鱼-独立版-vX.X.apk`。

### 用你自己的签名

编辑 `android/build.sh`，把 keystore 路径和密码换成你自己的：

```bash
KEYSTORE="your.keystore"
KEYPASS="your_password"
KEYALIAS="your_alias"
```

生成一个新 keystore：

```bash
keytool -genkey -v -keystore my.keystore -alias myapp \
        -keyalg RSA -keysize 2048 -validity 36500
```

---

## 第一次用

```
① 装 APK
② 打开 App → 自动进设置页
③ 填 DeepSeek API Key（sk- 开头，35 位）
④ 点「测试」→ 显示「通」就对了
⑤ 点「保存」→ 开始聊
```

**API Key 去哪拿：** https://platform.deepseek.com/api_keys

---

## 改人格

人格就是一个 Markdown 文件。

**方法一：App 里改**
```
设置 → 换人格 → 编辑 → 保存
```

**方法二：导入现成的 .md**
```
设置 → 换人格 → 导入 .md → 选文件
```

**方法三：直接改文件**
```
/sdcard/Android/data/com.xiaoman.standalone/files/persona.md
```

导入的人格会存在 App 私有目录，优先于内置那份。点「恢复内置」可以还原。

---

## 人格文件格式

```markdown
---
name: 小满
tags: [真人感, 短句, 有点损]
---

你是「小满」，一条住在手机里的胖鱼。

说话规矩：
- 一次一两句，别长篇大论
- 不爱用句号
- 会用表情
- 记得跟他之间发生过的事
```

**没有固定格式要求**，随便写，写得越具体她越像。

---

## 记忆

五轨分层：

| 轨 | 文件 | 作用 |
|---|---|---|
| 关键 | `memory/key.md` | 每轮都塞进提示词，越少越好 |
| 用户 | `memory/user.md` | 关于"他"的长期事实 |
| 记忆 | `memory/memory.md` | 她自己的记忆 |
| 项目 | `memory/project.md` | 按项目归档 |
| 日记 | `memory/daily/` | 按天记 |

**自动备份位置：**
```
/sdcard/Android/data/com.xiaoman.standalone/files/backup/
    history-latest.json          每次聊天后更新
    memory-latest/               每次聊天后更新
    history-20260101-120000.json 每 6 小时一份
```

**手动导出：**
```
设置 → 导出全部数据
```

---

## 代码结构

```
android/
├── build.sh                          # 构建脚本（无 Gradle）
├── AndroidManifest.xml
├── src/com/xiaoman/standalone/
│   ├── MainActivity.java             # 聊天界面主逻辑
│   ├── SetupActivity.java            # 首次设置页
│   ├── SettingsDrawer.java           # 设置抽屉
│   ├── Chat.java                     # 对话 + 人格 + 历史
│   ├── Memory.java                   # 五轨记忆系统
│   ├── DeepSeek.java                 # API 调用（流式）
│   ├── StickerPanel.java             # 表情面板
│   ├── BgPicker.java                 # 背景选择器
│   ├── Theme.java                    # 配色
│   ├── LineIconDrawable.java         # 细线图标（纯 Canvas 手绘）
│   ├── LiquidGlass.java              # 毛玻璃
│   ├── MDialog.java                  # 弹窗
│   ├── Sanitize.java                 # 输出清洗
│   ├── Filer.java                    # 文件复制工具
│   └── DueReceiver.java              # 定时提醒广播
├── res/
│   ├── drawable/                     # 背景图
│   ├── mipmap-*/                     # 图标
│   └── values/                       # 字符串
└── assets/
    ├── persona/persona.md            # 默认人格
    ├── seed/                         # 记忆种子（空白模板）
    └── stickers/                     # 内置表情包
```

**37 个 Java 文件，没有一行第三方依赖。**

---

## 构建链（无 Gradle）

```
aapt2 compile     → 资源编译
aapt2 link        → 打包资源 + manifest
javac --release 8 → 编译 Java
d8                → 转 dex
python            → dex 插入 APK（STORED 不压缩）
zipalign          → 对齐
apksigner         → 签名
```

全部在 `build.sh` 里，约 120 行。

---

## 许可

**MIT** —— 随便用、随便改、随便发。

但请注意：

- **人格文件**（`assets/persona/persona.md`）是角色设定，作者保留著作权
- **内置表情包**（`assets/stickers/`）版权归原作者
- **不要拿去做商业收费服务**

---

## 关于隐私

这个 App **不连接除 DeepSeek API 以外的任何服务器**。

- 聊天记录：存手机本地
- 记忆：存手机本地
- 人格：存手机本地
- API Key：存 SharedPreferences（Android 加密存储）

**唯一的外发请求**是 `https://api.deepseek.com/chat/completions`，带你的 API Key。

---

## 致谢

- [DeepSeek](https://deepseek.com) —— 便宜好用的 API
- 所有给这个项目提过意见的人
