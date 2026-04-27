# 🎮 SSAPI-Minecraft v2

SSAPI-Minecraft v2 는 숲(SOOP) 과 치지직의 후원·미션을 마인크래프트 서버 액션으로 연동하는 플러그인입니다.

> **v2.0 신규**: 다중 액션 / 범위 매칭 / 인게임 GUI / 미션 훅 (도전·대결미션) / 동시송출 연동 / 킷 시스템

## 목차

1. [소개](#-소개)
2. [빠른 시작 (5분)](#-빠른-시작-5분)
3. [명령어](#-명령어)
4. [후원 흐름](#-후원이-들어왔을-때-무엇이-일어나나)
5. [미션 기능](#-미션-기능-신규)
6. [킷 시스템](#-킷-시스템-신규)
7. [설정 파일 4종](#-설정-파일-4종)
8. [권한 노드](#-권한-노드)
9. [자주 쓰는 작업 빠른 경로](#-자주-쓰는-작업-한눈에)
10. [자주 묻는 질문 (FAQ)](#-자주-묻는-질문-faq)
11. [호환성](#-호환성)
12. [지원](#-지원--문의)

## 📋 소개

SSAPI 는 숲·치지직 후원·미션 데이터를 통합 스키마로 제공하는 서비스입니다. 이 플러그인은 그 데이터를 받아 **금액별·종류별로 마인크래프트 액션**(아이템 지급, 몹 소환, 텔포, 효과, 커맨드 실행 등)을 발동시킵니다.

**누구에게 필요한가?**
- 시청자 후원에 따라 게임 안에서 이벤트를 발생시키고 싶은 스트리머
- 미션 정산 시점에 보상을 지급하고 싶은 서버 운영자
- 동시송출(SOOP + 치지직) 후원을 모두 받고 싶은 분

## 🚀 빠른 시작 (5분)

1. **API 키 발급** — https://ssapi.kr/app/applications/new
2. **플러그인 설치** — `plugins/` 폴더에 jar 복사 후 서버 시작
3. **API 키 입력** — `plugins/SSApi/config.yml` 의 `api.key` 에 발급받은 키 붙여넣기
4. **서버 재시작** (또는 `/API관리 리로드`)
5. **연동** — 인게임에서 `/API 연동 숲 streamer2022` (또는 `/API 연동 치지직 12c5af...`)
6. **시작** — `/API 시작`

테스트는 `/API테스트 1100` 으로 1,100원 후원을 시뮬레이션할 수 있습니다.

## 📜 명령어

대소문자 모두 인식 — `/api`, `/API`, `/API관리` 모두 동작.

### 사용자 명령어 — `/API`

```
/API 연동 <숲|치지직> <id>           — 메인 연동
/API 동시송출연동 <숲|치지직> <id>   — 동시송출 추가 연동 (다른 플랫폼만 가능)
/API 동시송출해제                    — 동시송출 연동만 해제
/API 시작                            — 후원 보상 받기 시작
/API 중지                            — 후원 보상 받기 중지
/API 상태                            — 내 연동 현황
```

### 관리자 명령어 — `/API관리` (`ssapi.admin` 권한)

```
/API관리                              — GUI 열기 (메인 메뉴)
/API관리 도움말 [페이지]              — 도움말
/API관리 상태                         — 시스템 / 미션 설정 상태
/API관리 리로드                       — 설정 다시 불러오기 (atomic, 검증 실패 시 기존 유지)
/API관리 저장                         — 메모리 상태 디스크에 flush
/API관리 디버그 <on|off>              — 디버그 로깅 토글

/API관리 시작 <플레이어>              — 다른 플레이어 보상 시작
/API관리 중지 <플레이어>              — 다른 플레이어 보상 중지
/API관리 연동 <플레이어> <숲|치지직> <id>  — 다른 플레이어 메인 연동
/API관리 동시송출연동 <플레이어> ...
/API관리 동시송출해제 <플레이어>

/API관리 킷 <목록|편집|지급|추가|삭제|이름>     — 킷 관리 (GUI 권장)
/API관리 트리거 <목록|토글>           — 트리거 관리 (GUI 권장)
/API관리 미션                         — 미션 설정 확인 (대시보드 링크)

/API관리 설정 <검증|복구|백업|원본>   — 설정 파일 관리
/API관리 확인 <토큰>                  — 파괴적 작업 확인
```

### 테스트 명령어 — `/API테스트` (`ssapi.command.test`)

```
/API테스트 <금액> [플레이어]                       — 후원 synthetic
/API테스트 미션 receive [금액] [플레이어]          — 미션 receive synthetic
/API테스트 미션 settle [총금액] [후원자수] [플레이어]  — 미션 settle synthetic
/API테스트 미션 result [플레이어]                  — 미션 result synthetic
```

테스트 페이로드는 `_test: true` 마킹으로 운영 데이터와 분리됩니다.

## 🔄 후원이 들어왔을 때 무엇이 일어나나

```
SOOP/치지직 후원
       │
       ▼
SSAPI 서버 (통합 스키마로 정규화)
       │
       ▼  Socket.IO (Snappy 압축)
플러그인 — DonationListener
       │
       ▼  TriggerMatcher (priority desc + stop_on_match)
매칭된 트리거의 actions 순차 실행
       │
       ├── command          (서버 명령어, lines 여러 줄 가능)
       ├── give_kit         (kits.yml 의 킷 지급)
       ├── spawn_mob        (랜덤 몹 소환)
       ├── random_effect    (랜덤 포션 효과)
       ├── random_teleport  (랜덤 위치)
       └── instant_death    (즉사)
```

한 트리거에 여러 액션, 한 후원에 여러 트리거 매칭 가능 (priority + stop_on_match 로 제어).

## 🎯 미션 기능 (신규)

도전미션(혼자 진행)·대결미션(스트리머 두 명 vs)·참가미션(다른 사람 미션에 합류)을 모두 지원.

### 시점 선택 (대시보드에서)

세 가지 phase 중 원하는 것을 켤 수 있습니다 (기본: settle 만):

- **receive** — 미션 후원이 들어올 때마다
- **settle** — 미션 정산 시점 (도전미션 종료 / 대결미션 결과 확정)
- **result** — 미션 결과 메타가 도착했을 때 (SUCCESS / FAILURE / DRAW)

### 정산 처리 방식 (config.yml)

`mission.settle_payout`:
- `combined` (기본) — 미션의 모든 후원 금액을 합쳐서 트리거 1회
- `individual` — 후원자별로 트리거 N회 (tick_spacing 간격)

### 동시 다중 미션

한 스트리머가 여러 미션을 동시에 진행하면 각 `mission_key` 별로 독립 처리됩니다. A 미션 settle 이 B 미션 후원과 섞이지 않습니다.

## 🎁 킷 시스템 (신규)

CMI 스타일의 킷 시스템 — 인벤토리 슬롯별로 아이템 묶음을 저장하고 트리거 액션으로 지급.

**가장 쉬운 방법**: `/API관리` GUI 에서 킷 메뉴 → 새 킷 → 인벤토리에 아이템 드래그 → 닫기 (자동 저장).

수동: `kits.yml` 파일이나 `/API관리 킷 추가 <이름>` 명령.

## 📁 설정 파일 4종

| 파일 | 책임 | 자동 쓰기 | 주석 보존 |
|---|---|---|---|
| `config.yml` | API 키, 소켓, 스토리지, 미션 처리, 액션 옵션 | ❌ | ✅ |
| `triggers.yml` | 후원 / 연동 직후 트리거 | GUI 편집 시 | 헤더만 |
| `kits.yml` | 킷 (GUI 가 자동 관리) | 자주 | 헤더만 |
| `messages.yml` | 모든 채팅 / 알림 메시지 | ❌ | ✅ |

미션 트리거는 `config.yml [3] mission` 섹션의 `on_receive` / `on_settle` / `on_result` 에서 정의합니다.

### 빠른 경로

| 작업 | 위치 |
|---|---|
| API 키 변경 | `config.yml` 의 `api.key` |
| 후원 금액별 액션 추가 | `/API관리` GUI → 트리거 |
| 킷 만들기 | `/API관리` GUI → 킷 |
| 메시지 색깔 변경 | `messages.yml` (MiniMessage 포맷) |
| 메시지 표시 안 하기 | `messages.yml` 의 해당 키를 빈 문자열 `""` 로 |
| 사운드 끄기 | `config.yml` 의 `sounds.<type>: false` |
| 설정 검증 | `/API관리 설정 검증` |
| 주석 복구 | `/API관리 설정 복구 <파일명>` |

## 🔐 권한 노드

LuckPerms 호환 — 부모 권한 + 세분화 권한 트리.

| 권한 | 기본 | 설명 |
|---|---|---|
| `ssapi.use` | true | 도움말, 상태 (모든 사용자) |
| `ssapi.admin` | op | 모든 관리자 권한 (children 포함) |
| `ssapi.command.connect` | op | 다른 플레이어 연동 |
| `ssapi.command.start/stop` | op | 다른 플레이어 시작/중지 |
| `ssapi.command.reload` | op | 설정 리로드 |
| `ssapi.command.save` | op | 메모리 상태 저장 |
| `ssapi.command.test` | op | 테스트 이벤트 |
| `ssapi.kit.edit` | op | 킷 편집 |
| `ssapi.trigger.edit` | op | 트리거 편집 |
| `ssapi.gui` | op | GUI 열기 |

## 🧠 자주 쓰는 작업 한눈에

| 하고 싶은 것 | 명령어 / 경로 |
|---|---|
| 새 킷 만들기 | `/API관리` → 킷 → 새 킷 |
| 1만원 후원에 다이아 1개 지급 | `/API관리` → 트리거 → 새 트리거 (op=eq, value=10000, action=give_kit) |
| 메시지 한 줄만 비활성화 | `messages.yml` 에서 그 줄을 `""` 로 |
| 설정 파일 검증 | `/API관리 설정 검증` |
| 주석을 다 지웠는데 복구하고 싶음 | `/API관리 설정 복구 messages.yml` |
| 1,100원 후원 시뮬 | `/API테스트 1100` |
| 미션 정산 시뮬 | `/API테스트 미션 settle 100000 10` |

## ❓ 자주 묻는 질문 (FAQ)

**Q. 동시송출인데 두 플랫폼 모두 받고 싶어요.**
먼저 `/API 연동` 으로 메인 연동 → `/API 동시송출연동` 으로 다른 플랫폼 추가. 같은 플랫폼은 등록 불가.

**Q. 후원 메시지가 너무 시끄러워요.**
`messages.yml` 에서 해당 키를 빈 문자열로 설정. 사운드는 `config.yml` 의 `sounds.donation: false`.

**Q. 메시지 색이나 꾸밈을 바꾸고 싶어요.**
[MiniMessage 가이드](https://docs.advntr.dev/minimessage/format.html) 참고. 예: `<red>빨간 글씨</red>`, `<click:run_command:'/foo'>[클릭]</click>`.

**Q. 오프라인 플레이어에게도 보상을 지급하고 싶어요.**
`config.yml` 의 `reward.execute_when_offline: true`. 단, 인벤토리 의존 액션(`give_kit`, `random_teleport`, `instant_death`)은 자연 실패 — `command` 액션만 의미 있음. EssentialsX `mail send` 같은 우편함 플러그인이 있을 때만 권장.

**Q. 오류가 나는데 원인을 모르겠어요.**
`/API관리 디버그 on` → 콘솔 로그 확인. 설정 검증은 `/API관리 설정 검증`.

## 💻 호환성

- Java 17+
- Paper 1.20.4+
- 마인크래프트 1.20.4 미만은 미지원

## 🤝 지원 / 문의

- 디스코드: https://discord.gg/...
- 이슈 트래커: https://github.com/.../issues
- SSAPI 공식: https://ssapi.kr

## 📄 라이선스

[LICENSE](LICENSE) 참조.
