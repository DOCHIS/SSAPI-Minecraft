import os
import subprocess
from datetime import datetime
import stat
import sys

# 로그 파일 경로 설정
LOG_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'logs/setup.log')

def log_message(message: str):
    """로그 메시지를 기록합니다"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)
    with open(LOG_FILE, 'a', encoding='utf-8') as f:
        f.write(f"[{timestamp}] {message}\n")

def create_post_commit_hook():
    """post-commit 훅 파일을 생성합니다"""
    hook_content = '''#!/bin/bash
mkdir -p ./.github/logs
echo "=== Post-commit hook started at $(date) ===" >> "./.github/logs/hook.log"

# 현재 커밋 메시지 확인
COMMIT_MSG=$(git log -1 --pretty=%B)
echo "Current commit message: $COMMIT_MSG" >> "./.github/logs/hook.log"

if [ "$COMMIT_MSG" = "auto" ]; then
    echo "Running generator script..." >> "./.github/logs/hook.log"
    if [ -f "./.github/hooks/commit_msg_generator.py" ]; then
        # Python 실행 시 현재 디렉토리를 Git 루트로 설정
        cd "$(git rev-parse --show-toplevel)"
        PYTHONPATH="./.github/hooks"
        export PYTHONPATH
        python "./.github/hooks/commit_msg_generator.py" 2>&1 | tee -a "./.github/logs/hook.log"
        echo "Generator script executed" >> "./.github/logs/hook.log"
    else
        echo "Generator script not found" >> "./.github/logs/hook.log"
    fi
else
    echo "Not an auto commit, skipping" >> "./.github/logs/hook.log"
fi

echo "=== Post-commit hook finished at $(date) ===" >> "./.github/logs/hook.log"
echo "" >> "./.github/logs/hook.log"
'''
    
    # Git 저장소 루트 경로 찾기
    git_root = subprocess.check_output(['git', 'rev-parse', '--git-dir'], text=True).strip()
    hooks_dir = os.path.join(git_root, 'hooks')
    hook_path = os.path.join(hooks_dir, 'post-commit')
    
    # 훅 파일 생성 (UTF-8, LF)
    with open(hook_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(hook_content)
    
    # 실행 권한 부여
    os.chmod(hook_path, 0o755)
    
    print(f"post-commit 훅 파일이 생성되었습니다: {hook_path}")

def create_prepare_commit_msg_hook():
    """prepare-commit-msg 훅 파일을 생성합니다"""
    hook_content = '''#!/bin/bash
mkdir -p ./.github/logs
echo "=== Prepare-commit-msg hook started at $(date) ===" >> "./.github/logs/hook.log"

# 커밋 메시지 파일 읽기
COMMIT_MSG=$(cat "$1")
echo "Current commit message: $COMMIT_MSG" >> "./.github/logs/hook.log"

if [ "$COMMIT_MSG" = "auto" ]; then
    echo "Running generator script..." >> "./.github/logs/hook.log"
    if [ -f "./.github/hooks/commit_msg_generator.py" ]; then
        # Python 실행 시 현재 디렉토리를 Git 루트로 설정
        cd "$(git rev-parse --show-toplevel)"
        PYTHONPATH="./.github/hooks"
        export PYTHONPATH
        python "./.github/hooks/commit_msg_generator.py" 2>&1 | tee -a "./.github/logs/hook.log"
        echo "Generator script executed" >> "./.github/logs/hook.log"
    else
        echo "Generator script not found" >> "./.github/logs/hook.log"
    fi
else
    echo "Not an auto commit, skipping" >> "./.github/logs/hook.log"
fi

echo "=== Prepare-commit-msg hook finished at $(date) ===" >> "./.github/logs/hook.log"
echo "" >> "./.github/logs/hook.log"
'''
    
    git_root = subprocess.check_output(['git', 'rev-parse', '--git-dir'], text=True).strip()
    hooks_dir = os.path.join(git_root, 'hooks')
    hook_path = os.path.join(hooks_dir, 'prepare-commit-msg')
    
    with open(hook_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(hook_content)
    
    os.chmod(hook_path, 0o755)
    print(f"prepare-commit-msg 훅 파일이 생성되었습니다: {hook_path}")

def setup_git_hooks():
    """Git hooks 설정을 확인하고 필요한 경우 설정합니다"""
    try:
        git_root = subprocess.check_output(['git', 'rev-parse', '--git-dir'], text=True).strip()
        hooks_dir = os.path.join(git_root, 'hooks')
        
        # post-commit 훅 설정
        post_commit_path = os.path.join(hooks_dir, 'post-commit')
        if not os.path.exists(post_commit_path):
            log_message("post-commit 훅 파일을 생성합니다.")
            create_post_commit_hook()
        else:
            log_message("post-commit 훅 파일이 이미 존재합니다.")
            
        # prepare-commit-msg 훅 설정
        prepare_commit_msg_path = os.path.join(hooks_dir, 'prepare-commit-msg')
        if not os.path.exists(prepare_commit_msg_path):
            log_message("prepare-commit-msg 훅 파일을 생성합니다.")
            create_prepare_commit_msg_hook()
        else:
            log_message("prepare-commit-msg 훅 파일이 이미 존재합니다.")
            
    except Exception as e:
        log_message(f"Git hooks 설정 중 오류 발생: {str(e)}")
        raise

if __name__ == "__main__":
    setup_git_hooks() 