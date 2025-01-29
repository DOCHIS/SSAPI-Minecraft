import subprocess
import os
import anthropic
from typing import Tuple

def get_git_diff() -> str:
    """스테이징된 변경사항의 diff를 가져옵니다"""
    try:
        return subprocess.check_output(['git', 'diff', '--cached']).decode('utf-8')
    except subprocess.CalledProcessError:
        return ""

def generate_commit_message(diff: str) -> Tuple[str, str]:
    """Claude API를 사용하여 커밋 메시지와 설명을 생성합니다"""
    client = anthropic.Client(os.getenv('ANTHROPIC_API_KEY'))
    
    prompt = f"""다음 git diff를 분석하여 한글로 커밋 메시지와 설명을 작성해주세요.
    커밋 메시지는 50자 이내로, 설명은 72자 이내의 여러 줄로 작성해주세요.
    
    Git Diff:
    {diff}
    
    다음 형식으로 응답해주세요:
    제목: [커밋 메시지]
    설명: [상세 설명]
    """
    
    response = client.messages.create(
        model="claude-3-sonnet-20240229",
        max_tokens=300,
        temperature=0.7,
        messages=[{"role": "user", "content": prompt}]
    )
    
    # 응답에서 제목과 설명 추출
    content = response.content[0].text
    title_line = [line for line in content.split('\n') if line.startswith('제목:')][0]
    desc_lines = [line for line in content.split('\n') if line.startswith('설명:')]
    
    title = title_line.replace('제목:', '').strip()
    description = '\n'.join([line.replace('설명:', '').strip() for line in desc_lines])
    
    return title, description

def main():
    diff = get_git_diff()
    if not diff:
        print("스테이징된 변경사항이 없습니다.")
        return
    
    title, description = generate_commit_message(diff)
    
    # 커밋 메시지 파일에 직접 쓰기
    with open(os.environ.get('GIT_COMMIT_MSG_FILE', '.git/COMMIT_EDITMSG'), 'w') as f:
        f.write(f"{title}\n\n{description}")
    
    print("커밋 메시지가 생성되었습니다.")

if __name__ == "__main__":
    main() 