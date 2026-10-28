# 오늘 뭐 먹지? 메뉴 추천 게시판

웹서비스설계 JWT 게시판 과제입니다

## 작성자와 서비스 소개

- 학번 / 이름: 202212971 / 유기태
- 서비스 목적과 대상 사용자: 점심,저녁 메뉴를 정하지 못하는 친구와 동기들이 서로 메뉴를 추천해 주는 게시판이다. 누구나 추천 글을 읽을 수 있고, 글 작성은 로그인한 사용자만, 수정과 삭제는 작성자 본인만 할 수 있다. 로그인은 JWT로 처리하며 서버가 토큰을 발급하고 검증한다.
- 선택한 기술: Vue 3.5.43 + Spring Boot 3.5.16
- 주요 기능: 공개 목록, 상세 조회, 로그인과 JWT 발급, 내 정보 조회, 로그인 사용자의 글 작성, 작성자 본인의 수정·삭제, 토큰 만료와 로그아웃 처리, SQLite 파일 DB 저장

## 프로젝트 구조

```text
.
├── README.md
├── .gitignore
├── frontend-vue/      # Vue 프론트 
├── backend-spring/    # Spring Boot 백엔드
├── tools/             
└── docs/
    ├── report.pdf
    └── evidence/      # 테스트 증거 캡처
```

## 실행 환경

| 항목 | 사용 버전 |
|---|---|
| 운영체제 | Windows 11 Home (10.0.26200) |
| 프론트 런타임 Node.js | v24.21.0 (npm 11.19.0) |
| 백엔드 런타임 | JDK 21.0.12 |
| 프레임워크 | Vue 3.5.43 (Vite 8.3.4), Spring Boot 3.5.16, JJWT 0.13.0 |
| 데이터 저장 | SQLite (sqlite-jdbc 3.51.3.0) |

## 설치와 실행

### 1 저장소 복제

```sh
git clone https://github.com/ilililil-gif/jwt.git
cd jwt
```

### 2 백엔드 준비와 실행

Windows PowerShell:

```powershell
cd backend-spring
$b = New-Object byte[] 32
(New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($b)
$env:JWT_SECRET = -join ($b | ForEach-Object { $_.ToString('x2') })
.\mvnw.cmd package
java -jar target/jwt-board-1.0.0.jar
```

macOS/Linux:

```sh
cd backend-spring
export JWT_SECRET=$(openssl rand -hex 32)
chmod +x mvnw
./mvnw package
java -jar target/jwt-board-1.0.0.jar
```

- 환경설정 예시 파일: `backend-spring/env.example.txt`
- JWT_SECRET 설정 방법: 64자리 16진수(32바이트) 난수를 직접 생성해 환경변수로 주입한다. 비밀 키 값은 기록하지 않는다.
- TOKEN_TTL: 토큰 유효 시간(초). 기본값은 90이며, 만료 테스트는 실행 전 `TOKEN_TTL=3`처럼 설정해 짧게 바꾼다.
- 백엔드 주소: http://127.0.0.1:8000
- 정상 실행 확인 방법: 브라우저에서 http://127.0.0.1:8000/api/health 를 열어 `status`가 `ok`인지 확인한다.

### 3 프론트 빌드와 로컬 실행

백엔드를 켜 두고 새 터미널에서 실행합니다.

```sh
cd frontend-vue
npm ci
npm run build
npm run preview
```

- 최종 확인 주소: http://127.0.0.1:4173
- 개발 중 실행: `npm run dev`, http://127.0.0.1:5173
- API 연결 방식: 브라우저가 보낸 `/api` 요청을 Vite 프록시(`vite.config.js`의 `server.proxy`, `preview.proxy`)가 8000번 백엔드로 전달한다. 백엔드 포트를 바꾸면 두 곳을 모두 바꾼다.
- 종료: 프론트와 백엔드를 실행한 각 터미널에서 Ctrl+C
- 코드 변경 후 재실행: 프론트는 `npm run build`를 다시 한 뒤 `npm run preview`, 백엔드는 다시 빌드하고 서버를 재시작한다.

### 4 테스트 계정과 DB

| 사용자 | 이메일 | 실습용 비밀번호 |
|---|---|---|
| Alice | alice@class.test | Classroom!2026 |
| Bob | bob@class.test | Classroom!2026 |

- SQLite 파일 위치와 자동 생성 방식: `backend-spring/data/board.sqlite` (서버를 처음 실행할 때 자동 생성)
- DB 초기화 방법: 서버를 종료한 뒤 `backend-spring/data/board.sqlite`를 삭제하고 다시 실행한다.
- 서버 재시작 후 데이터 유지 확인 결과: 글 작성 후 서버를 종료·재실행해도 글, 작성자, 내용이 유지되었다. (테스트 T15) 저장소를 새 폴더에 clone해 같은 절차로 실행했을 때도 재시작 후 글이 유지되었다.

위 계정은 과제 전용 테스트 계정입니다. 서버 비밀 키는 기록하지 않습니다.

## 구현 완료 범위

| 기능 | 완료 여부 | 주요 파일 또는 함수 |
|---|---|---|
| 공개 목록과 상세 | 완료 | `BoardController.java` (list, get) |
| 로그인과 JWT 발급 | 완료 | `BoardController.java/login()`, `AuthService.java/issueToken()`, `api.js/login()` |
| JWT 검증과 내 정보 조회 | 완료 | `AuthService.java/currentUserId()`, `BoardController.java/me()` |
| 글 작성과 작성자 저장 | 완료 | `BoardController.java/create()` (작성자는 토큰에서 결정) |
| 본인 글 수정·삭제 | 완료 | `AuthService.java/assertOwner()`, `BoardController.java` (update, delete), `api.js/canEdit()` |
| 토큰 만료와 로그아웃 | 부분 | `api.js/request()`, `api.js/clearSession()`, `App.vue/logout()` |
| 입력 오류와 권한 오류 처리 | 완료 | `BoardController.java/fields()`, `Errors.java` |

알려진 문제와 미완성 기능:

- 의도된 제한(과제 범위): 새로고침하면 로그인 상태가 해제되고, 로그아웃해도 이미 발급된 토큰은 만료까지 서버에서 유효하다.
- 남은 문제: 404일 때 목록을 자동으로 갱신하지 않고, 401일 때 서버 메시지 외에 별도의 재로그인 안내 문구가 없다.

## 테스트 방법과 결과 요약

- 실행한 검사: Postman과 브라우저 개발자 도구(Network)로 T01~T17을 직접 수행했다.
- Alice/Bob 권한 구분 확인 결과: Bob이 Alice의 글을 수정·삭제하면 `403 FORBIDDEN`이고 글은 그대로이며, 본인 글은 수정·삭제가 성공했다 (T07, T08, T17).
- HTTP 상태와 오류 code 확인: 200, 201, 204와 400 BAD_INPUT, 401(INVALID_CREDENTIALS, TOKEN_REQUIRED, INVALID_TOKEN, TOKEN_EXPIRED), 403 FORBIDDEN, 404 NOT_FOUND를 실제 응답으로 확인했다. (보고서 04절)
- 응답 헤더와 본문 확인: 201 `Location`, 보호 API 401의 `WWW-Authenticate: Bearer`, 204 빈 본문을 확인했다.
- 토큰 없음·변조·만료 확인 결과: 각각 `TOKEN_REQUIRED`, `INVALID_TOKEN`, `TOKEN_EXPIRED`이며 데이터는 바뀌지 않았다 (T04, T09, T10).
- 전체 T01~T17 결과와 증거: 보고서 04절과 `docs/evidence/` 참고

## 핵심 구현 설명

로그인하면 서버(`AuthService.issueToken`)가 HS256으로 서명한 JWT를 발급하고, 프론트(`api.js`)는 토큰과 사용자 정보를 브라우저 메모리에만 보관한다. 보호 API를 부를 때 `Authorization: Bearer` 헤더로 토큰을 보내고, 서버는 `AuthService.currentUserId`로 서명·만료·발급자·대상·필수 클레임을 검증해 사용자를 확인한다. 글을 수정하거나 삭제할 때는 `AuthService.assertOwner`가 요청한 사람과 글 작성자를 비교해 다르면 403으로 거부한다. 자세한 설명은 보고서에 있다.

## AI 활용 요약과 참고 자료

- AI 사용 여부: 사용
- 도구와 사용 목적: Claude Code 개념 학습, 코드 초안 작성, 보고서 문장 정리 및 보고서를 기반으로 리드미 작성
- 반영 위치와 직접 수정·검증한 내용: `AuthService.java`, `api.js`의 초안을 바탕으로 주석과 응답 메시지, 화면 문구를 직접 수정했고, T01~T17을 직접 수행해 동작을 검증했다.
- 참고한 수업 자료·외부 코드·공식 문서: 6주차 강의 자료(JWT 로그인과 인증), 과제 제공 문서(공지, API 명세, 체크리스트, 테스트 시나리오)