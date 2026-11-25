# 自动发布

GitHub Actions 校验每个面向 `main` 的 PR。合并或直接推送到 `main` 后，工作流构建 Debug 与 Release、运行单元测试和 Android Lint，然后在独立的发布任务中签名并上传 APK、SHA-256 校验文件和自动生成的版本说明。手动运行工作流仅执行构建验证。

版本由提交决定。从切换自动发布前的 `96be29c`（1.0.7，versionCode 8）开始，沿 main 第一父提交链每前进一次，补丁版本和 versionCode 各增加 1。重跑同一提交沿用原版本。连续推送时优先发布最新提交，版本号可能跳号。切勿重写 main 的历史。

## 维护与恢复

- 发布任务只对 main 开放。签名使用 GitHub 的 `release` 环境中的 `KEYSTORE_BASE64`（原 PKCS#12 文件的 Base64）和 `KEYSTORE_PASSWORD`。保留原密钥的独立备份；首次配置完成后，日常发版无需访问维护者的电脑。
- 工作流检查 APK 包名、版本号和签名证书，并将 versionCode 与上一个正式发布的 APK 比较。签名证书沿用 v1.0.7，确保已有用户能覆盖安装。
- 失败时打开 **Actions → Android** 查看出错步骤。构建失败时可下载诊断报告。临时故障修复后选择 **Re-run failed jobs**。尚未公开的草稿可续传，已经发布的版本保留原附件。
- 在下载目录运行 `shasum -a 256 -c SoundState-<版本>.apk.sha256` 校验 APK。
- Dependabot 每周检查 Actions、每月检查 Gradle 依赖。合并更新前查看 CI 结果，合并到 main 即触发正式发布。

构建验证与发布职责的划分参考 [Now in Android](https://github.com/android/nowinandroid/tree/main/.github/workflows) 和 [Termux](https://github.com/termux/termux-app/tree/master/.github/workflows)。Actions 固定到提交 SHA，只有发布任务获得仓库写权限，依据 [GitHub Actions 安全指南](https://docs.github.com/en/actions/reference/security/secure-use)。
