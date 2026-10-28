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

目前已迁移主题、播放器图标按钮、搜索输入及按钮、主菜单和设置入口以及共享确认弹窗。其他 XML 和 Java 动态构建的界面仍需逐页迁移；不能将主题切换视作全部组件迁移。播放器受屏幕空间限制，当前部分按钮小于建议的 48dp 点击目标，仍需重新安排交互以达到该目标。实机验证应覆盖搜索、播放、歌词手势、音量、菜单、设置、登录、弹窗及自定义背景，并检查长文本、字体缩放与小屏幕裁切。
