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

已迁移主题与颜色角色；播放器图标按钮、功能面板、歌词页与确认弹窗；搜索与登录表单；主菜单、设置入口、开关；收藏/歌单/识别/个人中心等主要页面；列表项触控目标；对话框统一为 Material 样式。剩余少数二级页面仍使用动态 TextView 充当按钮，颜色与布局已统一为 Material 角色，但结构上尚未全部替换为 Material 组件。播放器按钮保留 48dp 点击区域、缩小视觉外观以适配 180dp 高度。

实机验证应覆盖搜索、播放、歌词手势、音量、菜单、设置、登录、弹窗及自定义背景，并检查长文本、字体缩放与小屏幕裁切。
