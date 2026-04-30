# 这是一套项目级ai提示词

- 1.本软件是一款为小天才电话手表编写的听歌软件，通过调用网易云的API实现功能，手表的dpi是320x360,Android 7-8.1，设计用md2，UI设计需要符合整体主题
- 2.网易云API参考这个仓库: https://github.com/nooblong/NeteaseCloudMusicApiBackup ,需要实现新的功能是直接在客户端实现与neteaseMusicAPI交互，而不是调用三方音乐服务器
- 3.所有UI设计一定要能适配手表界面320*360dpi
- 4.版本号的格式为年月日(-fix{number}),例：20260331 20260331-fix1 20260331-fix2
- 5.每当我和你说发布xxx版本时，请你将version code的加1，并且将versionname改成对应的值