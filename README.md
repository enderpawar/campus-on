# 캠퍼스 온 (가칭)

서울과학기술대학교 학생용 올인원 플랫폼의 기획 명세와 인터랙티브 화면 목업이다. 학교 포털과 e-class를 그대로 쓰면서, 일정(Planary), 학습 자료 요약, 학교 공지, 복지·취업 추천을 한 화면에 모은다.

> 화면 검토용 목업이다. 모든 수업, 공지, 프로그램 정보는 예시 값이며 실제 학교 계정, AI, 신청 서비스와 연결되어 있지 않다.

## 목업 실행

설치나 빌드가 필요 없다. 저장소를 받은 뒤 `index.html`을 브라우저로 연다. 글꼴이 로컬 파일이라 간단한 정적 서버로 여는 것이 가장 정확하다.

```bash
npx http-server -p 4180 -c-1 .
# http://127.0.0.1:4180/index.html
```

## 팀 공유용 화면 명세

- [화면 갤러리](docs/review/index.html): 화면별 데스크톱·모바일·주요 상태 이미지와 동작 명세를 함께 본다. 설치 없이 파일을 열거나 정적 서버에서 `/docs/review/`로 접속한다.
- [공유 자료 안내](docs/review/README.md): 화면 목록, PNG 명세 보드, 원본 캡처, 압축파일 사용법.
- `docs/review/boards/`: 화면별 이미지 명세서 9장. 메신저나 발표 자료에 첨부할 수 있다.
- `docs/review/captures/`: 최신 목업에서 직접 캡처한 화면·상태 이미지 30장.

## 문서

| 문서 | 내용 |
| --- | --- |
| [구현 명세 v0.1](캠퍼스%20올인원%20플랫폼%20구현%20명세%20v0.1.md) | 서비스 개요, 범위, 기능(F1~F6), e-class 연동 전략, 아키텍처, 데이터 모델, 리스크, 수익 모델, 역할 분담 |
| [00. 공통 요소](docs/screens/00-공통.md) | 상단 바, 배너, 알림·프로필 팝오버, 테크뇽 버튼, 디자인 규칙, 반응형 기준 |
| [01. 홈](docs/screens/01-홈.md) | 나의 하루: 요약 줄, 다음 수업, 각 구역 배치 |
| [02. 나의 일정](docs/screens/02-나의-일정.md) | Planary 주간 날짜 띠, 할 일 목록, 빈 날짜·모두 완료 상태 |
| [03. 학습](docs/screens/03-학습.md) | e-class 형식 수강과목 카드 |
| [04. 학교 소식](docs/screens/04-학교-소식.md) | 분류 탭, 부서별 공지 목록 |
| [05. 복지·취업](docs/screens/05-복지-취업.md) | 신청 가능한 프로그램 카드 |
| [06. 테크뇽 패널](docs/screens/06-테크뇽-추천-패널.md) | 학과·학년 맞춤 추천 4건, 학습 자료 기준 질문(학습 도우미) |
| [07. 상세보기 · 일정 추가](docs/screens/07-상세보기-일정추가.md) | 대화상자 두 종류와 상세 키 목록 |
| [08. 학습 자료](docs/screens/08-학습-자료.md) | 주차별 자료, 원본 위치, 요약·핵심 개념·퀴즈·플래시카드 |

각 화면 명세는 개요, 화면 구성, 데이터(예시 값), 동작, 상태, 반응형, 미결 사항 순서로 쓰고, 1440px과 390px 화면 캡처를 붙였다.

## 파일 구성

| 파일 | 역할 |
| --- | --- |
| `index.html` | 화면 구조 |
| `styles.css` | 화면별 규칙, 반응형, 애니메이션 |
| `tokens.css` | 색, 글꼴, 글자 크기, 간격, 모서리, 그림자, 움직임 시간 |
| `base.css` | 공통 기본값(포커스 표시, 동작 줄이기 등) |
| `app.js` | 예시 데이터와 브라우저 안의 동작 |
| `assets/` | 캠퍼스·필기·서가 사진, 테크뇽 공식 그림, Pretendard JP 글꼴(SIL OFL) |
| `docs/screens/` | 화면별 명세와 캡처 |
| `architecture.png`, `bot-reference.png` | 아키텍처 그림, 테크뇽 BOT 참고 이미지 |

## 자산 사용

테크뇽은 서울과학기술대학교 공식 캐릭터이고 캠퍼스 사진은 학교 포털 이미지에서 가져왔다. 팀 내부 검토용으로만 쓰며, 외부 공개 전에 학교의 사용 허가를 확인해야 한다.

요약 카드의 필기·서가 사진과 과목 카드의 추상 이미지는 Unsplash 이미지를 WebP로 내려받아 사용한다. 사진은 실제 학교의 학습 공간이 아니며, 과목 커버는 참고 화면의 이미지를 그대로 쓰지 않고 별도의 이미지를 사용한다.

- `assets/summary-writing.webp`: [필기 사진 원본](https://images.unsplash.com/photo-1455390582262-044cdead277a)
- `assets/summary-library.webp`: [서가 사진 원본](https://images.unsplash.com/photo-1507842217343-583bb7270b66)
- `assets/course-database.webp`: [블루 파동 추상 이미지](https://images.unsplash.com/photo-1634017839464-5c339ebe3cb4)
- `assets/course-os.webp`: [진주빛 추상 이미지](https://images.unsplash.com/photo-1618005198919-d3d4b5a92ead)
- `assets/course-web.webp`: [흐르는 색면 추상 이미지](https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe)
- 이용 조건: [Unsplash License](https://unsplash.com/license)
