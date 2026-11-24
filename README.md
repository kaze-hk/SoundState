<p align="center"><img src="docs/soundstate.svg" width="88" alt="SoundState 声波图标"></p>
<h1 align="center">SoundState · 声态</h1>
<p align="center">一个随手可调的 Android 声音面板。<br>
<sub>A floating volume panel for Android 7.0+ · 简体中文 / English</sub></p>

<p align="center">
  <a href="https://github.com/kaze-hk/SoundState/releases/latest"><img height="40" alt="从 GitHub Releases 下载 APK" src="https://img.shields.io/badge/GitHub_Releases-下载_APK-39594f?style=for-the-badge&amp;logo=github&amp;logoColor=white"></a>
  <img height="40" src="docs/google-play.svg" alt="Google Play · 敬请期待">
</p>

## 从下拉菜单开始

将 **声态** 添加到快捷设置。开会前轻点一下磁贴就能从响铃切到振动；听音乐时长按磁贴，声音面板会浮在当前应用上方。

| 操作 | 效果 |
| --- | --- |
| 单击磁贴 | 在响铃、振动、静音间循环，磁贴图标随系统状态更新 |
| 长按磁贴 | 打开面板，分别调节媒体、铃声和闹钟音量 |
| 拖动面板顶部的短横条 | 调整面板高度，下次打开时恢复 |
| 点按面板外侧 | 立即收起；松手后默认空闲 6 秒也会自动收起 |

面板跟随系统深浅主题，在支持动态配色的设备上使用壁纸色。

## 安装

1. 打开 [GitHub Releases](https://github.com/kaze-hk/SoundState/releases/latest)，从 **Assets** 下载 APK，在 Android 7.0 或更高版本的设备上安装。
2. 打开声态，点 **添加快捷按钮**。Android 12 及更早版本可在快捷设置的编辑界面手动添加。
3. 若切换静音时出现权限提示，请按引导授予声态勿扰模式使用权。

## 本地构建

使用 Android Studio 打开项目根目录，安装 Android SDK 37，使用项目自带的 Gradle Wrapper 构建。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。

## 许可

项目原创代码和资源采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，允许按条款进行非商业使用、修改和分发。商业授权请与权利人另行约定。第三方组件及 Gradle Wrapper 遵循各自的许可。
