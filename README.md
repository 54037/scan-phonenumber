# 面单拨号（BillPhone）

扫描快递面单 -> 自动识别 11 位手机号 -> 确认拨打 -> 挂断后点"下一单"继续扫。

## 技术栈
- CameraX：相机预览 + 帧分析
- ML Kit 中文印刷体识别：模型随 APK 打包，完全离线，无网络请求
  （依赖 `com.google.mlkit:text-recognition-chinese`）
- 命中号码后锁定（beep 提示），防止重复触发

## 编译运行
1. 用 Android Studio 打开本目录（会提示同步 Gradle，按提示即可；
   AGP/Kotlin 版本在 `build.gradle` 顶部，可自行调整为你本地已装的版本）。
2. 真机运行（模拟器相机不方便），授予相机权限。
3. 对准打印面单上的手机号，识别到后底部显示号码并响提示音，
   点"确认拨打"跳系统拨号盘，挂断回来点"下一单"。

## 识别速度说明
- 分析流固定 1280x720 + KEEP_ONLY_LATEST，中端机每帧识别约几十~一百多毫秒，
  基本"对准即出"。
- 手机号正则在整页 OCR 文本上去掉空白后匹配，地址、单号不会误命中。

## 如需"免点确认、直拨"
把 `call()` 改为 `Intent(Intent.ACTION_CALL, ...)`，
AndroidManifest 增加 `<uses-permission android:name="android.permission.CALL_PHONE"/>`，
并在运行时申请。注意：上架应用市场时 ACTION_CALL 属于敏感权限，需声明用途。

## 如需更快/更省电（进阶，可选）
通用 OCR 仍在整页跑。若要做到每帧 <30ms 或更省电：
训练"数字行检测 + 仅数字字符集识别"的小模型（YOLOv8n 检测 + CRNN 识别，
字符集仅 0-9），用 NCNN 部署。打印字体规整，合成数据即可训练到很高准确率。

## 不用装 Android Studio：GitHub Actions 自动出 APK
1. 在 GitHub 新建一个仓库，把本目录全部上传（或 `git push`）。
2. 打开仓库的 **Actions** 标签页，选中 "Build APK"，点 **Run workflow**。
3. 等几分钟（绿色对勾）后，点进这次运行，底部 **Artifacts** 里下载
   `billphone-debug-apk`，里面就是 `app-debug.apk`。

## 装到手机上
1. 把 APK 传到手机（微信文件传输、数据线均可）。
2. 点击安装，按提示允许"安装未知来源应用"。
3. 首次打开授予相机权限，对准面单即可使用。
（debug 签名的 APK 仅供自用，不能上架应用市场。）

## 版本说明（2026-11 更新）
- Android Gradle Plugin 8.5.2 + Kotlin 1.9.24 + Gradle 8.7
- compileSdk / targetSdk 34（AGP 8.5 官方支持的最高版本，本 App 用不到 35 的特性）
- 需要 Android Studio Koala (2024.1) 或更新版本 + JDK 17。
  打开工程后如果提示 Gradle 同步/创建 wrapper，点同意即可；
  若用命令行，先在工程目录执行一次 `gradle wrapper` 生成 gradlew。
