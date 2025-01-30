import os
import subprocess
import sys
import anthropic
import json

class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def log_message(message: str, status: str = "INFO"):
    """로그 메시지를 콘솔에 출력합니다"""
    status_indicators = {
        "INFO": f"{Colors.BLUE}💬 {Colors.RESET}",
        "SUCCESS": f"{Colors.GREEN}💫 {Colors.RESET}",
        "WARNING": f"{Colors.YELLOW}⭐ {Colors.RESET}",
        "ERROR": f"{Colors.RED}❌ {Colors.RESET}",
        "DEBUG": f"{Colors.YELLOW}💠 {Colors.RESET}",
        "START": f"{Colors.BLUE}🔷 {Colors.RESET}",
        "COMPLETE": f"{Colors.GREEN}💫 {Colors.RESET}",
        "PROCESS": f"{Colors.BLUE}💠 {Colors.RESET}",
        "STASH": f"{Colors.YELLOW}💠 {Colors.RESET}",
        "RESTORE": f"{Colors.GREEN}💫 {Colors.RESET}",
        "API": f"{Colors.BLUE}💠 {Colors.RESET}",
    }
    
    indicator = status_indicators.get(status, status_indicators["INFO"])
    print(f"{indicator} {message}")

def get_api_key() -> str:
    """API 키를 환경 변수 또는 .env 파일에서 가져옵니다"""
    try:
        api_key = os.getenv('ANTHROPIC_API_KEY')
        if api_key:
            log_message("환경 변수에서 API 키를 찾았습니다.", "SUCCESS")
            return api_key
            
        env_path = os.path.join(os.path.dirname(__file__), '.env')
        log_message(f"[DEBUG] .env 파일 경로: {env_path}", "DEBUG")
        log_message(f"[DEBUG] 파일 존재 여부: {os.path.exists(env_path)}", "DEBUG")
        
        if os.path.exists(env_path):
            with open(env_path, 'r', encoding='utf-8') as f:
                content = f.read().strip()
                if content.startswith('ANTHROPIC_API_KEY='):
                    log_message(".env 파일에서 API 키를 찾았습니다.", "SUCCESS")
                    return content.split('=', 1)[1].strip()
                else:
                    return content.strip()
                        
        raise ValueError("API 키를 찾을 수 없습니다. .env 파일을 확인해주세요.")
        
    except Exception as e:
        log_message(f"API 키 가져오기 실패: {str(e)}", "ERROR")
        raise

def extract_json_from_response(content: str) -> dict:
    """Claude API 응답에서 JSON 부분을 추출합니다"""
    try:
        # JSON 문자열을 찾아 파싱
        return json.loads(content)
    except json.JSONDecodeError:
        log_message("JSON 파싱 실패, 응답 내용에서 JSON 부분 추출 시도", "WARNING")
        # JSON 형식이 아닌 경우, 응답에서 JSON 부분만 추출
        try:
            start = content.find('{')
            end = content.rfind('}') + 1
            if start != -1 and end != 0:
                json_str = content[start:end]
                return json.loads(json_str)
        except:
            log_message("JSON 추출 실패", "ERROR")
            raise ValueError("응답에서 유효한 JSON을 찾을 수 없습니다")

def get_commit_changes(commit_hash: str) -> str:
    """특정 커밋의 변경사항을 가져옵니다"""
    try:
        # 스테이징된 변경사항 확인
        status = subprocess.run(
            ['git', 'status', '--porcelain'], 
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True
        )
        has_staged_changes = bool(status.stdout.strip())
        
        if has_staged_changes:
            log_message("스테이지된 변경사항 발견, stash에 저장", "STASH")
            # stash 명령어 실행
            subprocess.run(
                ['git', 'stash', 'push', '-m', "Temporary stash before commit message generation"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
        
        try:
            result = subprocess.run(
                ['git', 'show', commit_hash],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding='utf-8'
            )
            
            if result.returncode == 0 and result.stdout:
                log_message(f"커밋 {commit_hash}의 변경사항 가져오기 성공", "SUCCESS")
                return result.stdout
            else:
                log_message(f"변경사항 가져오기 실패: {result.stderr}", "ERROR")
                return ""
        finally:
            if has_staged_changes:
                log_message("스테이지된 변경사항 복원", "RESTORE")
                subprocess.run(
                    ['git', 'stash', 'pop'],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL
                )
                
    except subprocess.CalledProcessError as e:
        log_message(f"변경사항 가져오기 실패: {str(e)}", "ERROR")
        return ""

def generate_commit_message(diff: str, max_tokens: int = 1000) -> tuple[str, str]:
    """Claude API를 사용하여 커밋 메시지를 생성합니다"""
    try:
        api_key = get_api_key()
        log_message("API 클라이언트 생성 시작", "PROCESS")
        client = anthropic.Anthropic(api_key=api_key)
        log_message("API 클라이언트 생성 완료", "SUCCESS")
        
        prompt = f"""Git diff를 분석하여 커밋 메시지를 작성해주세요.

        응답 형식:
        반드시 다음 JSON 형식으로 응답해주세요:
        {{
            "title": "접두사: 커밋 메시지 (50자 이내)",
            "description": "상세 설명 (각 줄 72자 이내)"
        }}

        Git Diff:
        {diff}
        """
        
        log_message("Claude API 호출", "API")
        response = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=max_tokens,
            temperature=0.7,
            messages=[{
                "role": "user", 
                "content": prompt
            }]
        )
        log_message("Claude API 응답 수신", "SUCCESS")
        
        content = response.content[0].text
        result = extract_json_from_response(content)
        
        return result['title'], result['description']
        
    except Exception as e:
        log_message(f"커밋 메시지 생성 중 오류: {str(e)}", "ERROR")
        raise

def update_commit_message(commit_hash: str, title: str, description: str) -> None:
    """생성된 메시지로 커밋 메시지를 수정합니다"""
    try:
        message = f"{title}\n\n{description}"
        log_message("커밋 메시지 수정 시작", "PROCESS")
        
        result = subprocess.run(
            ['git', 'commit', '--amend', '-m', message],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            encoding='utf-8'
        )
        
        if result.returncode == 0:
            log_message("커밋 메시지가 성공적으로 수정되었습니다.", "SUCCESS")
        else:
            log_message(f"커밋 메시지 수정 실패: {result.stderr}", "ERROR")
            raise Exception("커밋 메시지 수정 실패")
            
    except Exception as e:
        log_message(f"커밋 메시지 수정 중 오류: {str(e)}", "ERROR")
        raise

def get_user_input() -> tuple[str, int]:
    """사용자로부터 커밋 해시와 토큰 수를 입력받습니다"""
    try:
        log_message("커밋 해시를 입력해주세요:", "INFO")
        commit_hash = input().strip()
        
        while True:
            log_message("토큰 수를 입력해주세요 (기본값: 1000):", "INFO")
            token_input = input().strip()
            
            if not token_input:  # 빈 입력이면 기본값 사용
                return commit_hash, 1000
                
            try:
                tokens = int(token_input)
                if tokens > 0:
                    return commit_hash, tokens
                log_message("토큰 수는 양수여야 합니다.", "WARNING")
            except ValueError:
                log_message("유효하지 않은 토큰 수입니다. 숫자를 입력해주세요.", "WARNING")
                
    except KeyboardInterrupt:
        log_message("\n프로그램을 종료합니다.", "INFO")
        sys.exit(0)

def main():
    try:
        commit_hash, max_tokens = get_user_input()
        log_message(f"=== 커밋 메시지 생성 시작 (commit_hash: {commit_hash}, tokens: {max_tokens}) ===", "START")
        
        changes = get_commit_changes(commit_hash)
        if not changes:
            log_message("변경사항을 가져올 수 없습니다.", "ERROR")
            return
            
        title, description = generate_commit_message(changes, max_tokens)
        log_message("생성된 커밋 메시지:", "INFO")
        log_message(f"제목: {title}", "INFO")
        log_message(f"설명: {description}", "INFO")
        
        update_commit_message(commit_hash, title, description)
        log_message("커밋 메시지 수정 완료", "COMPLETE")
        
    except Exception as e:
        log_message(f"오류 발생: {str(e)}", "ERROR")
        raise

if __name__ == "__main__":
    main()
