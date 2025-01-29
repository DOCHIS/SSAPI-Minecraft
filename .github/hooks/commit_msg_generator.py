import os
import subprocess
from typing import Tuple
import anthropic
import sys
import json
import traceback
import re

# 한글 인코딩 설정
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

def log_message(message: str):
    """로그 메시지를 기록합니다"""
    try:
        with open("./.github/logs/hook.log", "a", encoding='utf-8') as f:
            f.write(f"{message}\n")
            f.flush()  # 즉시 디스크에 쓰기
    except Exception as e:
        print(f"로그 쓰기 실패: {str(e)}")

def get_last_commit_diff() -> str:
    """마지막 커밋의 diff를 가져옵니다"""
    try:
        # diff 가져오기 (UTF-8 인코딩 지정)
        result = subprocess.run(
            ['git', 'show', 'HEAD'],
            capture_output=True,
            text=True,
            encoding='utf-8'
        )
        if result.returncode == 0:
            log_message(f"Diff 가져오기 성공: {len(result.stdout)} 바이트")
            return result.stdout
        else:
            log_message(f"Diff 가져오기 실패: {result.stderr}")
            return ""
            
    except subprocess.CalledProcessError as e:
        log_message(f"Diff 가져오기 실패: {str(e)}")
        return ""

def get_api_key() -> str:
    """API 키를 .env 파일에서 읽어옵니다"""
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        env_path = os.path.join(script_dir, '.env')
        
        # 로그 디렉토리 생성
        os.makedirs("./.github/logs", exist_ok=True)
        
        log_message("\n[DEBUG] === API 키 디버그 시작 ===")
        log_message(f"[DEBUG] 1. 현재 스크립트 위치: {script_dir}")
        log_message(f"[DEBUG] 2. env 파일 경로: {env_path}")
        
        # 1. 파일 존재 확인
        if not os.path.exists(env_path):
            log_message("[ERROR] 3. ❌ .env 파일이 없음")
            sys.exit(1)
        
        log_message("[DEBUG] 3. ✅ .env 파일 존재함")
        
        # 2. 파일 내용 읽기
        with open(env_path, 'r', encoding='utf-8') as f:
            content = f.read()
            log_message(f"[DEBUG] 4. 파일 크기: {len(content)} bytes")
            
            # 파일 내용 출력 (앞 10자리만)
            preview = content[:10] + "..." if len(content) > 10 else content
            log_message(f"[DEBUG] 5. 파일 내용 미리보기: {preview}")
            
            # 공백 제거
            api_key = content.strip()
            
            if not api_key:
                log_message("[ERROR] 6. ❌ API 키가 비어있음")
                sys.exit(1)
            
            # 3. API 키 검증
            if len(api_key) < 30:
                log_message(f"[ERROR] 6. ❌ API 키가 너무 짧음 ({len(api_key)} chars)")
                sys.exit(1)
                
            log_message(f"[DEBUG] 6. ✅ API 키 확인됨 (길이: {len(api_key)})")
            log_message(f"[DEBUG] 7. API 키 미리보기: {api_key[:4]}...")
            log_message("[DEBUG] === API 키 확인 완료 ===\n")
            
            return api_key
            
    except Exception as e:
        log_message(f"[ERROR] ❌ 치명적 오류: {str(e)}")
        log_message(f"[ERROR] 스택 트레이스: {traceback.format_exc()}")
        sys.exit(1)

def update_version_in_gradle(version_change: dict) -> None:
    """build.gradle 파일의 버전을 업데이트합니다"""
    try:
        if not version_change['should_update']:
            return
            
        gradle_path = 'build.gradle'
        with open(gradle_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # 현재 버전 찾기
        version_pattern = r"version\s*=\s*'(\d+)\.(\d+)\.(\d+)'"
        match = re.search(version_pattern, content)
        if not match:
            log_message("버전 정보를 찾을 수 없습니다")
            return
            
        release, major, minor = map(int, match.groups())
        
        # 버전 업데이트
        if version_change['type'] == 'major':
            major += 1
            minor = 0
        elif version_change['type'] == 'minor':
            minor += 1
            
        new_version = f"{release}.{major}.{minor}"
        new_content = re.sub(version_pattern, f"version = '{new_version}'", content)
        
        # 파일 업데이트
        with open(gradle_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
            
        log_message(f"버전이 {new_version}로 업데이트되었습니다")
        
        # 변경된 build.gradle 파일을 스테이징에 추가
        subprocess.run(['git', 'add', gradle_path], check=True)
        
    except Exception as e:
        log_message(f"버전 업데이트 중 오류 발생: {str(e)}")
        raise

def extract_json_from_response(content: str) -> dict:
    """Claude API 응답에서 JSON 객체를 추출합니다"""
    try:
        # 모든 중괄호 쌍의 위치를 찾습니다
        stack = []
        json_ranges = []
        
        for i, char in enumerate(content):
            if char == '{':
                stack.append(i)
            elif char == '}' and stack:
                start = stack.pop()
                if not stack:  # 가장 바깥쪽 중괄호 쌍을 찾았을 때
                    json_ranges.append((start, i + 1))
        
        # 찾은 모든 JSON 후보들을 파싱해봅니다
        for start, end in json_ranges:
            try:
                json_str = content[start:end]
                result = json.loads(json_str)
                
                # 필요한 키들이 모두 있는지 확인
                if all(key in result for key in ['title', 'description', 'version_update']):
                    return result
            except json.JSONDecodeError:
                continue
                
        raise ValueError("유효한 JSON 객체를 찾을 수 없습니다")
        
    except Exception as e:
        log_message(f"JSON 추출 중 오류: {str(e)}")
        raise

def generate_commit_message(diff: str) -> Tuple[str, str]:
    """Claude API를 사용하여 커밋 메시지와 설명을 생성합니다"""
    try:
        api_key = get_api_key()
        log_message("API 클라이언트 생성 시작")
        client = anthropic.Anthropic(api_key=api_key)
        log_message("API 클라이언트 생성 완료")
        
        # 현재 버전 읽기
        current_version = "1.0.0"  # 기본값
        try:
            with open('build.gradle', 'r', encoding='utf-8') as f:
                content = f.read()
                match = re.search(r"version\s*=\s*'(\d+\.\d+\.\d+)'", content)
                if match:
                    current_version = match.group(1)
        except Exception as e:
            log_message(f"현재 버전 읽기 실패: {str(e)}")
        
        prompt = f"""Git diff를 분석하여 커밋 메시지를 작성하고 버전 업데이트 여부를 결정해주세요.
        현재 버전: {current_version}

        응답 형식:
        반드시 다음 JSON 형식으로 응답해주세요:
        {{
            "title": "접두사: 커밋 메시지 (50자 이내)",
            "description": "상세 설명 (각 줄 72자 이내)",
            "version_update": {{
                "should_update": true/false,
                "type": "major/minor/none",
                "next_version": "x.y.z",
                "reason": "버전 업데이트 이유 설명"
            }}
        }}

        버전 업데이트 기준:
        1. Major 업데이트 (y+1, z=0):
           - 새로운 주요 기능 추가
           - API 변경
           - DB 스키마 변경
           - Breaking changes 발생

        2. Minor 업데이트 (z+1):
           - 기존 기능의 작은 개선
           - 버그 수정
           - 성능 개선

        3. 업데이트 불필요:
           - 문서 수정
           - 주석 변경
           - 코드 포맷팅
           - README 업데이트

        Git Diff:
        {diff}
        """
        
        log_message("Claude API 호출 시작")
        response = client.messages.create(
            model="claude-3-5-sonnet-20241022",  # 최신 모델로 업데이트
            max_tokens=300,
            temperature=0.7,
            messages=[{
                "role": "user", 
                "content": prompt
            }]
        )
        log_message("Claude API 응답 받음")
        
        content = response.content[0].text
        log_message(f"Claude 응답: {content}")
        
        # 새로운 JSON 추출 함수 사용
        result = extract_json_from_response(content)
        
        title = result.get('title', '').strip()
        description = result.get('description', '').strip()
        version_update = result.get('version_update', {})
        
        if not title:
            raise ValueError("커밋 메시지가 비어있습니다.")
            
        log_message(f"생성된 제목: {title}")
        log_message(f"생성된 설명: {description}")
        
        if version_update.get('should_update'):
            update_version_in_gradle(version_update)
        
        return title, description
        
    except Exception as e:
        log_message(f"커밋 메시지 생성 중 오류: {str(e)}")
        raise

def main():
    try:
        log_message("\n=== 커밋 메시지 생성 시작 ===")
        diff = get_last_commit_diff()
        if not diff:
            log_message("커밋의 diff를 가져올 수 없습니다.")
            return
        
        title, description = generate_commit_message(diff)
        log_message("커밋 메시지 생성 완료")
        
        # 마지막 커밋 메시지 수정 (UTF-8 인코딩 지정)
        result = subprocess.run(
            ['git', 'commit', '--amend', '-m', f"{title}\n\n{description}"],
            capture_output=True,
            text=True,
            encoding='utf-8'
        )
        
        if result.returncode == 0:
            log_message("커밋 메시지가 업데이트되었습니다.")
        else:
            log_message(f"커밋 메시지 업데이트 실패: {result.stderr}")
        
    except Exception as e:
        log_message(f"오류 발생: {str(e)}")
        raise

if __name__ == "__main__":
    main() 