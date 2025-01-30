#!/bin/bash

# 색상 코드 정의
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# libs 폴더 정리
echo -e "\n🧹 ${BLUE}빌드 폴더 정리 중...${NC}"
rm -rf build/libs/*
echo -e "✨ ${GREEN}빌드 폴더 정리 완료${NC}"

# 플러그인 빌드
echo -e "\n🔨 ${BLUE}플러그인 빌드 시작...${NC}"
if ! ../gradle-8.12.1/bin/gradle build --quiet; then
    echo -e "❌ ${RED}빌드 실패!${NC}"
    exit 1
fi
echo -e "✅ ${GREEN}빌드 완료${NC}"

# 대상 경로 설정
TARGET_DIR="D:/OneDrive/문서/ServerEngine/servers/server_765119288/plugins"

# build/libs 폴더에서 jar 파일 찾기
JAR_FILE=$(find build/libs -name "ssapi-minecraft-*.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo -e "❌ ${RED}오류: 빌드된 jar 파일을 찾을 수 없습니다.${NC}"
    exit 1
fi

echo -e "📦 빌드된 파일: $(basename "$JAR_FILE")"

# 파일 복사 프로세스
echo -e "\n📝 ${BLUE}플러그인 배포 시작...${NC}"

# 대상 경로의 이전 버전 파일 제거
echo -e "🗑️  이전 버전 제거 중..."
if ! rm -f "$TARGET_DIR"/ssapi-minecraft-*.jar; then
    echo -e "⚠️  ${RED}이전 버전 제거 실패! 파일이 사용 중일 수 있습니다.${NC}"
    exit 1
fi
echo -e "✨ ${GREEN}이전 버전 제거 완료${NC}"

# 새 파일 복사
echo -e "📋 새 버전 복사 중..."
if ! cp "$JAR_FILE" "$TARGET_DIR"; then
    echo -e "❌ ${RED}파일 복사 실패!${NC}"
    exit 1
fi

echo -e "✅ ${GREEN}배포 완료!${NC}"
echo -e "📍 배포 위치: $TARGET_DIR/$(basename "$JAR_FILE")\n"