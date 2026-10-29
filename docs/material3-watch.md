# Material 3 手表界面

## 设计约束

目标设备的屏幕是 320×360 px，实测密度为 320 dpi，约等于 160×180 dp；它运行普通 Android，而不是 Wear OS。使用 Material Components for Android 的 Material 3 深色主题，保留 Android 6.0 兼容性，不使用需要较新系统的动态取色。

播放器优先展示曲名、播放进度和播放操作；其余操作放在菜单或功能面板。采用深色表面、色调容器和清晰的主操作层级，保留已有背景、播放与手势功能。列表和表单应允许滚动，避免在小屏幕上依赖固定的内容高度。

参考：

- [Material Design 3](https://m3.material.io/)
- [Material 3 in Compose：颜色角色与无障碍](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [手表设计原则](https://developer.android.com/design/ui/wear/guides/get-started/design-for-wearables/principles)
- [触控目标尺寸](https://developer.android.com/develop/ui/compose/accessibility/api-defaults#minimum-touch-target-sizes)

## 迁移状态

全部界面已完成 Material 3 组件替换：主题与颜色角色、播放器（图标按钮、Material Slider 进度条、音量指示条）、歌词页（翻译开关为色调按钮）、搜索、登录表单、评论（Material 输入框、发送按钮、长按操作对话框）、歌单/收藏/个人中心/识别/云盘/更新/Bilibili 全系页面、对话框统一为 Material 样式，进度指示使用 MaterialProgressIndicator。唯一的例外是 media3 播放器的 `exo_play_pause`：库内部将该控件强转为 `ImageButton` 并直接调用 `setImageResource`，组件类型必须保留，外观已按 M3 配色。

## 统一规范

- **度量基准**：代码内 `px()` 辅助函数与 `WatchUiUtils.px()` 均按屏幕密度换算（1 基准值 = 1dp），与 XML 中的 `sp`/`dp` 页面（以收藏页为基准：标题 14–15sp、正文 13sp、次要 11sp）保持一致；屏宽相对尺寸（歌曲详情大封面、B 站二维码、识曲唱片）单独按屏宽比例计算。
- **列表卡片**：歌曲、歌单、MV、云盘、历史、帮助条目统一使用 `bg_card_rounded`（elevated surface、16dp 圆角）卡片行，封面/头像用 `ShapeableImageView`（`Widget.App.ShapeImage`，8dp 圆角），列表分隔线移除，由卡片间距分隔。
- **对话框**：全局 `ThemeOverlay.App.MaterialAlertDialog`（标题 15sp、正文 13sp、按钮 12sp 单行）；列表菜单用 `WatchUiUtils.showActionDialog` 自定义卡片行。
- **输入框**：XML 用共享样式 `Widget.App.TextInputLayout`，代码用 `WatchUiUtils.createWatchTextInput`；只读展示框用 `bg_field_rounded`。
- **面板**：播放器信息等覆盖层使用 `bg_panel_rounded`（16dp 圆角）静态面板 + 内部滚动。

播放器标题与歌手两侧固定 48dp 对称边距：既保证文字始终居中于屏幕中线，又使长歌名的跑马灯限制在左上歌单按钮与右上更多按钮之间，不会压到按钮。

MV 播放时会自动暂停音乐，退出 MV 页面后音乐从暂停处继续。

实机验证应覆盖搜索、播放、歌词手势、音量、菜单、设置、登录、弹窗及自定义背景，并检查长文本、字体缩放与小屏幕裁切。
