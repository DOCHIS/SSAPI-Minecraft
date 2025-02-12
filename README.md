# 🎮 SSAPI-Minecraft

SSAPI-Minecraft는 숲과 치지직의 후원 및 채팅 데이터를 마인크래프트 서버에서 활용할 수 있게 해주는 플러그인입니다.

# 목차

- [개요](#-개요)
- [시스템 요구사항](#-시스템-요구사항)
- [명령어](#-명령어)
- [커스텀 커맨드](#-커스텀-커맨드)
- [API 지원 범위](#-api-지원-범위)
- [호환성](#-호환성)
- [후원 동작](#-후원-동작)
- [설정](#-설정)
- [의존성](#-의존성)
- [권한](#-권한)
- [시작하기](#-시작하기)
- [자주 묻는 질문 (FAQ)](#-자주-묻는-질문-faq)
- [라이선스](#-라이선스)

# 📋 개요

이 플러그인은 [SSAPI](https://ssapi.kr)를 기반으로 제작되었습니다. SSAPI는 숲(숲)과 치지직의 실시간 채팅과 후원 데이터를 통합적으로 제공하는 서비스입니다. 자세한 내용은 [SSAPI 공식 웹사이트](https://ssapi.kr)를 참고해주세요.

# 🔧 시스템 요구사항

- **Java 버전**: Java 17 이상
- **마인크래프트 버전**: 1.16.5 이상
- **지원 서버**: Paper (권장), Spigot, Bukkit 등 대부분의 버킷 기반 서버
  - Paper에서만 공식 테스트되었습니다.

# 📝 명령어

## 일반 사용자 명령어

| 명령어                            | 별칭         | 설명                   |
| --------------------------------- | ------------ | ---------------------- |
| `/api 연동 <숲\|치지직> <아이디>` | `/후원 연동` | 스트리밍 플랫폼과 연동 |
| `/api 시작`                       | `/후원 시작` | API 연동 시작          |
| `/api 중지`                       | `/후원 중지` | API 연동 중지          |

## 관리자 명령어 (`ssapi.admin` 권한 필요)

| 명령어                                           | 별칭                    | 설명                                       |
| ------------------------------------------------ | ----------------------- | ------------------------------------------ |
| `/api관리 리로드`                                | `/후원관리 리로드`      | 설정 파일 다시 불러오기                    |
| `/api관리 시작 <플레이어>`                       | `/후원관리 시작`        | 플레이어의 API 연동 시작                   |
| `/api관리 중지 <플레이어>`                       | `/후원관리 중지`        | 플레이어의 API 연동 중지                   |
| `/api관리 연동 <플레이어> <숲\|치지직> <아이디>` | `/후원관리 연동`        | 플레이어의 API 연동 설정                   |
| `/api관리 아이템 [플레이어]`                     | `/후원관리 아이템`      | 손에 든 아이템을 후원 지급 아이템으로 설정 |
| `/api관리 설정 초기화`                           | `/후원관리 설정 초기화` | 후원 금액별 동작 설정 초기화               |
| `/api관리 설정 추가 <금액> <동작>`               | `/후원관리 설정 추가`   | 후원 금액별 동작 추가                      |
| `/api관리 설정 삭제 <금액>`                      | `/후원관리 설정 삭제`   | 후원 금액별 동작 삭제                      |
| `/api관리 설정 확인`                             | `/후원관리 설정 확인`   | 후원 금액별 동작 목록 확인                 |

## 테스트 명령어 (`ssapi.admin` 권한 필요)

| 명령어                                | 별칭          | 설명             |
| ------------------------------------- | ------------- | ---------------- |
| `/api테스트 <금액/액션명> [플레이어]` | `/후원테스트` | 후원 액션 테스트 |

### 사용 가능한 액션 목록

| 액션명            | 설명           |
| ----------------- | -------------- |
| `GIVE_ITEM`       | 아이템 지급    |
| `RANDOM_EFFECT`   | 랜덤 효과 부여 |
| `SPAWN_MOB`       | 몹 소환        |
| `RANDOM_TELEPORT` | 랜덤 텔레포트  |
| `INSTANT_DEATH`   | 즉사           |

# 🛠️ 커스텀 커맨드

후원이 발생했을 때 실행할 커맨드를 직접 정의할 수 있습니다. 이를 통해 원하는 어떤 기능이든 구현이 가능합니다!

# 커맨드 설명

## donation 커맨드

후원이 발생했을 때 자동으로 실행되는 커맨드입니다. format에서 제공되는 변수들을 활용하여 후원 발생 시 실행될 커맨드를 원하는 대로 설정할 수 있습니다.

## streamernick 커맨드

이 플러그인은 API 연동 시 치지직/숲의 방송국 정보를 불러와서 해당 아이디가 올바른지 확인합니다. 이때 얻어온 닉네임으로 스트리머의 닉네임을 자동 설정하고 싶은 경우 이 명령어를 지정해서 사용할 수 있습니다. 실제로 많은 마인크래프트 서버에서 닉네임 설정 자동화 목적으로 사용하고 있습니다.

# 사용 가능한 변수

format에서 사용할 수 있는 변수들입니다:

| 변수             | 설명                 | 예시                                   |
| ---------------- | -------------------- | -------------------------------------- |
| `{id}`           | 후원 고유 ID         | 6791c65c2abf284c382e10cd               |
| `{platform}`     | 후원 플랫폼          | soop, chzzk                            |
| `{cnt}`          | 후원 개수            | 1                                      |
| `{amount}`       | 후원 금액            | 100 (치즈 개당 1원, 별풍선 개당 100원) |
| `{player}`       | 마인크래프트 닉네임  | 헛삯                                   |
| `{uuid}`         | 마인크래프트 UUID    | 620151a2-cb8c-4099-bbae-5700e6de19e0   |
| `{streamer_id}`  | 스트리머 ID          | test1                                  |
| `{donator_id}`   | 후원자 ID            | test2                                  |
| `{donator_name}` | 후원자 닉네임        | 테스트 후원자                          |
| `{message}`      | 후원 메시지 (치지직) | 에라잇 후원 받아랏                     |

# config.yml 설정 예시

config.yml에서 커스텀 커맨드를 다음과 같이 정의할 수 있습니다:

```yaml
commands:
  donation:
    enabled: true
    format: "donation {platform} {player} {cnt} {donator_name}"
  streamernick:
    enabled: true
    format: "setstreamernick {player} {nickname}"
```

이를 통해 후원 금액별로 다양한 커맨드를 실행하거나, 플러그인과 연동하여 더 많은 기능을 구현할 수 있습니다.

# 🎯 API 지원 범위

## 지원하는 기능

- ✅ 방송 중일 때의 후원
- ✅ 마인크래프트 접속 중일 때의 후원
- ✅ 후원 종류:
  - 숲
    - 별풍선
    - VOD 별풍선
    - 애드벌룬
    - VOD 애드벌룬
    - 방송국 애드벌룬
  - 치지직
    - 치즈

## 지원하지 않는 기능

- ❌ 도전미션, 대결미션 등 기타 후원
- ❌ 비밀번호가 설정된 방송
- ❌ 19금 방송
- ❌ 방송노출 안함으로 설정된 방송

# 🔄 호환성

## 서버 소프트웨어

- **Paper**: 1.16.5 이상 (권장, 테스트 완료)
- **Spigot/Bukkit**: 1.16.5 이상 (미테스트, 작동 가능)

> **참고**
> Paper 이외의 서버 소프트웨어에서는 테스트되지 않았으나, 특별한 서버 의존성이 없어 작동할 것으로 예상됩니다.

# 🎮 후원 동작

## 기본 제공 동작

### 1. 아이템 지급 (GIVE_ITEM)

![아이템 지급](.github/docs/GIVE_ITEM.gif)
관리자가 설정한 아이템을 후원자에게 지급합니다.

### 2. 즉시 사망 (INSTANT_DEATH)

![즉시 사망](.github/docs/INSTANT_DEATH.gif)
후원을 받은 플레이어를 즉시 사망시킵니다.

### 3. 랜덤 효과 (RANDOM_EFFECT)

![랜덤 효과](.github/docs/RANDOM_EFFECT.gif)
무작위로 버프 또는 디버프를 부여합니다.

### 4. 랜덤 텔레포트 (RANDOM_TELEPORT)

![랜덤 텔레포트](.github/docs/RANDOM_TELEPORT.gif)
플레이어를 무작위 위치로 순간이동시킵니다.

### 5. 몹 소환 (SPAWN_MOB)

![몹 소환](.github/docs/SPAWN_MOB.gif)
플레이어 주변에 무작위 몹을 소환합니다.

# ⚙️ 설정

## ⚠️ 주의사항

- 설정 정보와 후원 금액별 동작 설정은 MySQL을 사용하더라도 각 서버(버킷)별로 개별 설정해야 합니다.
- 서버 설정(`api.servers.*`)은 특별한 사유가 없다면 수정하지 마세요. 전용 API 서버를 사용하는 경우에만 변경이 필요합니다.

## API 설정

| 설정         | 설명                 | 기본값           |
| ------------ | -------------------- | ---------------- |
| `api.prefix` | API 메시지 프리픽스  | `§b[§eAPI§b] §f` |
| `api.key`    | SSAPI 인증 키 (필수) | `""`             |

## 서버 설정

> ⚠️ 전용 API 서버를 사용하는 경우가 아니라면 수정하지 마세요.

| 설정                 | 설명           | 기본값                    |
| -------------------- | -------------- | ------------------------- |
| `api.servers.api`    | API 서버 주소  | `https://api.ssapi.kr`    |
| `api.servers.socket` | 소켓 서버 주소 | `https://socket.ssapi.kr` |

## 소켓 설정

| 설정                                | 설명                             | 기본값 |
| ----------------------------------- | -------------------------------- | ------ |
| `api.socket.timeout`                | 연결 타임아웃 (밀리초)           | `5000` |
| `api.socket.reconnection.enabled`   | 재연결 시도 여부                 | `true` |
| `api.socket.reconnection.attempts`  | 최대 재시도 횟수 (-1: 무제한)    | `-1`   |
| `api.socket.reconnection.delay`     | 재연결 대기 시간 (밀리초)        | `1000` |
| `api.socket.reconnection.max-delay` | 최대 재연결 대기 시간 (밀리초)   | `5000` |
| `api.socket.login-retry-delay`      | 로그인 재시도 대기 시간 (밀리초) | `2000` |

## 데이터 저장소 설정

| 설정           | 설명                  | 기본값 |
| -------------- | --------------------- | ------ |
| `storage.type` | 저장 방식 (yml/mysql) | `yml`  |

### MySQL 설정 (`storage.type: "mysql"` 사용 시)

| 설정                                    | 설명                          | 기본값          |
| --------------------------------------- | ----------------------------- | --------------- |
| `storage.mysql.host`                    | MySQL 호스트                  | `localhost`     |
| `storage.mysql.port`                    | MySQL 포트                    | `3306`          |
| `storage.mysql.database`                | 데이터베이스 이름             | `your_database` |
| `storage.mysql.username`                | MySQL 사용자명                | `your_username` |
| `storage.mysql.password`                | MySQL 비밀번호                | `your_password` |
| `storage.mysql.pool.maximum-pool-size`  | 최대 커넥션 개수              | `10`            |
| `storage.mysql.pool.minimum-idle`       | 최소 유휴 커넥션 개수         | `5`             |
| `storage.mysql.pool.idle-timeout`       | 최대 유휴 시간 (밀리초)       | `300000`        |
| `storage.mysql.pool.max-lifetime`       | 커넥션 최대 수명 (밀리초)     | `1800000`       |
| `storage.mysql.pool.connection-timeout` | 커넥션 생성 타임아웃 (밀리초) | `30000`         |

## 후원 동작 설정

| 설정                        | 설명             |
| --------------------------- | ---------------- |
| `donation-actions.amounts`  | 금액별 동작 설정 |
| `donation-actions.settings` | 동작별 세부 설정 |

### 사용 가능한 동작 유형

- `GIVE_ITEM`: 아이템 지급
- `RANDOM_EFFECT`: 랜덤 버프/디버프
- `SPAWN_MOB`: 몹 소환
- `RANDOM_TELEPORT`: 랜덤 텔레포트
- `INSTANT_DEATH`: 즉시 사망

각 동작 유형별 상세 설정은 `config.yml`을 참고해 주세요.

### 로그 설정

| 설정                    | 설명                  | 기본값 |
| ----------------------- | --------------------- | ------ |
| `logging.enabled`       | 로그 저장 활성화 여부 | `true` |
| `logging.save.donation` | 후원 로그 저장        | `true` |
| `logging.save.failure`  | 실행 실패 로그 저장   | `true` |

로그는 선택한 저장소 타입(`storage.type`)에 따라 다르게 저장됩니다:

#### YML 저장소 사용 시

- JSON 형식의 텍스트로 저장
- 저장 위치: `plugins/SSApi/logs` 디렉토리
- 날짜별로 파일 구분

#### MySQL 저장소 사용 시

- `api_log` 테이블에 저장
- 테이블 구조:
  ```sql
  CREATE TABLE `api_log` (
      `log_no` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
      `server_ip` VARCHAR(15) NULL DEFAULT NULL,
      `server_name` VARCHAR(100) NULL DEFAULT NULL,
      `streamer_id` VARCHAR(100) NULL DEFAULT NULL,
      `username` VARCHAR(100) NULL DEFAULT NULL,
      `cnt` INT(10) UNSIGNED NULL DEFAULT NULL,
      `type` VARCHAR(50) NULL DEFAULT NULL,
      `property` TEXT NULL DEFAULT NULL,
      `isRun` ENUM('Y','N') NULL DEFAULT 'Y',
      `player_name` VARCHAR(100) NULL DEFAULT NULL,
      `player_uuid` CHAR(36) NULL DEFAULT NULL,
      `player_world` VARCHAR(100) NULL DEFAULT NULL,
      `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`log_no`)
  )
  ```

저장되는 로그 정보:

- 서버 정보 (IP, 이름)
- 스트리머 ID
- 후원자 정보 (이름, 금액, 개수)
- 실행 정보 (타입, 속성, 성공 여부)
- 플레이어 정보 (이름, UUID, 월드)
- 생성 시간

## 📚 의존성
=======

## 필수

- **Paper** (또는 Spigot/Bukkit): 1.16.5 이상
- **Java**: 17 이상

## 내부 라이브러리

- **Socket.IO Client**: 2.1.1 (실시간 이벤트 처리)
- **Engine.IO Client**: 2.1.0 (소켓 통신)
- **HikariCP**: 5.1.0 (MySQL 커넥션 풀)
- **MySQL Connector/J**: 9.2.0 (MySQL 연결)
- **OkHttp**: 4.12.0 (HTTP 통신)
- **JSON**: 20240303 (JSON 데이터 처리)
- **Snappy**: 1.1.10.5 (데이터 압축)

# 🔒 권한

| 권한          | 설명                                               | 기본값 |
| ------------- | -------------------------------------------------- | ------ |
| `ssapi.admin` | 관리자 명령어 사용 권한 (`/api관리`, `/api테스트`) | OP     |
| `ssapi.use`   | 일반 사용자 명령어 사용 권한 (`/api`)              | 모두   |

# 🚀 시작하기

1. [SSAPI 공식 웹사이트](https://ssapi.kr)에서 API 키를 발급받습니다.

   - API 키는 신청서 작성 후 승인을 통해 발급됩니다.
   - 자세한 발급 절차는 공식 웹사이트를 참고해 주세요.

2. 플러그인 설치

   - `plugins` 폴더에 플러그인 파일을 넣습니다.
   - 서버를 시작하면 자동으로 설정 파일이 생성됩니다.
   - `config.yml`에서 발급받은 API 키를 입력합니다.

> **참고**
>
> - API 연동 후 데이터가 들어오기까지 최대 15초가 소요될 수 있습니다.
> - 연동만 하고 시작을 하지 않는 경우가 많습니다. 반드시 시작 명령어까지 입력해 주세요!

# ❓ 자주 묻는 질문 (FAQ)

## API 연동 관련 오류

| 오류 메시지                                                            | 해결 방법                                                                                               |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| "이미 등록되어있습니다."                                               | 이미 연동이 완료된 상태입니다. 게임/방송 재시작 시에도 연동/시작 명령어를 다시 실행할 필요가 없습니다.  |
| "[API] 숲 스트리머아이디는 소문자, 숫자, \_, -로만 이루어져야 합니다." | 아이디 형식이 올바르지 않습니다. 올바른 형식으로 다시 입력해 주세요.                                    |
| "스트리머 정보 조회 실패" 또는 "스트리머 계정이 아닌것 같습니다."      | 아이디를 잘못 입력하셨습니다. 숲 아이디를 정확히 입력했는지 확인 후 연동을 처음부터 다시 시도해 주세요. |
| "데이터 추가 중 오류가 발생하였습니다."                                | 서버 측 오류입니다. 디스코드 문제 발생 채널에 문의해 주세요.                                            |
| "연동 중 알 수 없는 오류가 발생하였습니다."                            | 서버 측 오류입니다. 디스코드 문제 발생 채널에 문의해 주세요.                                            |
| "[API] 기존 데이터 삭제 중 오류가 발생하였습니다."                     | 서버 측 오류입니다. 디스코드 문제 발생 채널에 문의해 주세요.                                            |

# 📜 라이선스

이 플러그인은 MIT 라이선스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참고해 주세요.

SSAPI 서비스 자체의 라이선스와 이용 약관은 [SSAPI 공식 웹사이트](https://ssapi.kr)를 참고해 주세요.
