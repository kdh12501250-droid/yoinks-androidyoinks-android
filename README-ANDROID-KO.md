# yoinks 0.3.1 안드로이드 프로젝트 (Kotlin + Jetpack Compose + youtubedl-android)

원본 `yoinks-0.3.1` Node.js 터미널 앱의 UI(`src/app.tsx`), 테마(`src/theme.ts`), 플랫폼 감지(`src/lib/platforms.ts`), 해상도별 MP4 및 MP3 포맷 선택·다운로드 로직(`src/lib/ytdlp.ts`)을 안드로이드 네이티브 앱으로 이식한 프로젝트입니다.

## 주요 기능
- **원본 UI 및 테마 재현**: `yoinks` 고유의 블록 아트 로고, 모노스페이스 터미널 스타일, `auto` / `light`(`#ffffff`) / `dark`(`#18181b`) 테마 전환 지원
- **안드로이드 자체 내장 yt-dlp & FFmpeg**: `youtubedl-android` 라이브러리를 통해 별도 터미널 없이 앱 내부에서 YouTube, X/Twitter, Instagram, Threads, TikTok 등 1,800개 이상 사이트의 영상(해상도별 MP4 병합) 및 오디오(MP3) 추출
- **공유하기(Share Intent) 연동**: 유튜브·인스타그램·틱톡·X 앱에서 **공유하기 -> yoinks**를 누르면 링크가 자동 입력되어 즉시 포맷 선택 화면으로 진입
- **저장 경로**: 안드로이드 공용 `Download/yoinks` 폴더에 저장

---

## 프로그램 설치 없이 GitHub에서 자동으로 `.apk` 파일 만드는 방법 (모바일/PC 모두 가능)

이 프로젝트에는 `.github/workflows/build-apk.yml` 자동 빌드 워크플로우가 포함되어 있어, PC에 Android Studio를 설치하지 않아도 **GitHub 서버가 대신 `.apk` 파일을 빌드**해 줍니다.

1. [GitHub](https://github.com/)에서 새 저장소(Repository)를 생성합니다.
2. 이 압축 파일(`yoinks-0.3.1-android-project.zip`)의 압축을 풀고, 안의 파일(`app`, `.github`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`)을 그대로 저장소에 업로드(Push 또는 Upload files)합니다.
   - 주의: `.github/workflows/build-apk.yml` 폴더가 포함되어야 자동 빌드가 실행됩니다.
3. GitHub 저장소 상단 **Actions** 탭으로 이동하면 `Build yoinks Android APK` 워크플로우가 자동으로 실행됩니다 (약 3~5분 소요).
4. 빌드가 완료되면 해당 워크플로우 하단의 **Artifacts** 섹션에서 **`yoinks-0.3.1-debug-apk`**를 다운로드하고 압축을 풀어 스마트폰에 `app-debug.apk`를 설치합니다.

---

## PC의 Android Studio에서 직접 `.apk` 빌드하는 방법

1. **Android Studio**를 실행하고 **Open**을 눌러 압축을 푼 폴더를 엽니다.
2. Gradle Sync가 완료되면 상단 메뉴에서 **Build > Build Bundle(s) / APK(s) > Build APK(s)**를 클릭합니다.
3. 완료 알림에서 **locate**를 누르면 `app/build/outputs/apk/debug/app-debug.apk` 파일을 바로 확인할 수 있습니다.
