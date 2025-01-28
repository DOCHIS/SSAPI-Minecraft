#!/bin/bash

# 플러그인을 일반 jar로 빌드
echo "플러그인을 빌드 중..."
../gradle-8.12.1/bin/gradle build
echo

# 대상 경로 설정
TARGET_DIR="D:/OneDrive/문서/ServerEngine/servers/server_765119288/plugins"
JAR_FILE="build/libs/ssapi-minecraft-1.0.jar"  # 직접 파일명 지정

echo "빌드 완료! build/libs 폴더를 확인하세요."

# 파일 복사 프로세스 시작
echo "플러그인 파일을 서버 플러그인 폴더로 복사 중..."

# 대상 경로에 동일한 파일이 있는지 확인
if [ -f "$TARGET_DIR/$(basename $JAR_FILE)" ]; then
    echo "기존 플러그인 파일 제거 중..."
    rm "$TARGET_DIR/$(basename $JAR_FILE)"
fi

# 새 파일 복사
echo "새 플러그인 파일 복사 중..."
cp "$JAR_FILE" "$TARGET_DIR"

echo "복사 완료!"
echo "복사된 경로: $TARGET_DIR/$(basename $JAR_FILE)"