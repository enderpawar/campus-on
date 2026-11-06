"use strict";

const $ = (selector) => document.querySelector(selector);
const today = "2026-11-06";
let selectedDate = today;
let weekStart = new Date(2026, 10, 2, 12);
let taskId = 5;
const tasks = [
  {
    id: 1,
    date: today,
    title: "데이터베이스 설계 과제 제출",
    meta: ["데이터베이스", "23:59까지"],
    badge: "오늘 마감",
    kind: "urgent",
    done: false,
  },
  {
    id: 2,
    date: today,
    title: "10주차 온라인 강의 수강",
    meta: ["운영체제", "강의 영상 42분"],
    badge: "D-2",
    kind: "",
    done: false,
  },
  {
    id: 3,
    date: today,
    title: "캡스톤 디자인 팀 회의",
    meta: ["미래관 312호", "16:00 ~ 17:00"],
    badge: "개인 일정",
    kind: "personal",
    done: false,
  },
  {
    id: 4,
    date: "2026-11-04",
    title: "운영체제 스터디",
    meta: ["중앙도서관", "18:00"],
    badge: "개인 일정",
    kind: "personal",
    done: true,
  },
  {
    id: 5,
    date: "2026-11-08",
    title: "알고리즘 문제 풀이",
    meta: ["개인 학습", "14:00"],
    badge: "개인 일정",
    kind: "personal",
    done: false,
  },
];
const weekdayNames = ["일", "월", "화", "수", "목", "금", "토"];
function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
function parseDate(key) {
  const [year, month, day] = key.split("-").map(Number);
  return new Date(year, month - 1, day, 12);
}
function startOfWeek(key) {
  const date = parseDate(key);
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
  return date;
}
function dateLabel(key) {
  const date = parseDate(key);
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${weekdayNames[date.getDay()]}요일`;
}
function renderWeek() {
  const end = new Date(weekStart);
  end.setDate(end.getDate() + 6);
  $("#week-label").textContent =
    `${weekStart.getFullYear()}년 ${weekStart.getMonth() + 1}월 ${weekStart.getDate()}일 ~ ${end.getMonth() + 1}월 ${end.getDate()}일`;
  const fragment = document.createDocumentFragment();
  for (let i = 0; i < 7; i++) {
    const date = new Date(weekStart);
    date.setDate(date.getDate() + i);
    const key = dateKey(date),
      button = document.createElement("button");
    button.type = "button";
    button.className = "day-button";
    button.dataset.date = key;
    button.classList.toggle("selected", key === selectedDate);
    button.classList.toggle(
      "has-tasks",
      tasks.some((task) => task.date === key),
    );
    button.setAttribute("aria-pressed", String(key === selectedDate));
    button.setAttribute(
      "aria-label",
      `${dateLabel(key)}${key === today ? ", 오늘" : ""}`,
    );
    const label = document.createElement("span");
    label.textContent = weekdayNames[date.getDay()];
    const number = document.createElement("strong");
    number.textContent = date.getDate();
    button.append(label, number);
    fragment.append(button);
  }
  $("#week-grid").replaceChildren(fragment);
}
function renderTasks() {
  const visible = tasks.filter((task) => task.date === selectedDate);
  $("#task-date").textContent = dateLabel(selectedDate);
  $("#task-progress").textContent =
    `${visible.filter((task) => task.done).length} / ${visible.length} 완료`;
  const pendingToday = tasks.filter(
    (task) => task.date === today && !task.done,
  ).length;
  $("#pending-count").textContent = pendingToday;
  $("#hero-pending").textContent = pendingToday;
  const dueToday = tasks.filter(
    (task) => task.date === today && task.kind === "urgent" && !task.done,
  ).length;
  $(".summary-number.coral").textContent = dueToday;
  const fragment = document.createDocumentFragment();
  if (!visible.length) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.innerHTML =
      '<img src="assets/technyong-front.webp" alt="" width="56" height="77">이 날짜에 등록된 일정이 없어요.<p>일정 추가로 개인 일정을 넣을 수 있어요.</p>';
    fragment.append(empty);
  } else if (visible.every((task) => task.done)) {
    const done = document.createElement("div");
    done.className = "done-state";
    done.innerHTML = `<img src="assets/technyong-cheer.webp" alt="" width="64" height="64">${selectedDate === today ? "오늘" : `${dateLabel(selectedDate)}의`} 할 일을 모두 끝냈어요.`;
    fragment.append(done);
  }
  for (const task of visible) {
    const row = document.createElement("div");
    row.className = `task-row${task.done ? " done" : ""}`;
    const input = document.createElement("input");
    input.type = "checkbox";
    input.id = `task-${task.id}`;
    input.dataset.task = task.id;
    input.checked = task.done;
    const label = document.createElement("label");
    label.htmlFor = input.id;
    label.textContent = task.title;
    const small = document.createElement("small");
    small.className = "meta";
    for (const part of task.meta) {
      const span = document.createElement("span");
      span.textContent = part;
      small.append(span);
    }
    label.append(small);
    const badge = document.createElement("span");
    badge.className = `task-badge ${task.kind}`;
    badge.textContent = task.done ? "완료" : task.badge;
    row.append(input, label, badge);
    fragment.append(row);
  }
  $("#task-list").replaceChildren(fragment);
}
function refreshSchedule() {
  renderWeek();
  renderTasks();
}
$("#week-grid").addEventListener("click", (event) => {
  const button = event.target.closest("[data-date]");
  if (!button) return;
  selectedDate = button.dataset.date;
  refreshSchedule();
  $(`[data-date="${selectedDate}"]`).focus({ preventScroll: true });
});
$("#task-list").addEventListener("change", (event) => {
  const id = Number(event.target.dataset.task),
    task = tasks.find((item) => item.id === id);
  if (!task) return;
  task.done = event.target.checked;
  renderTasks();
  $(`#task-${id}`).focus({ preventScroll: true });
});
function moveWeek(offset) {
  weekStart.setDate(weekStart.getDate() + offset);
  selectedDate = dateKey(weekStart);
  refreshSchedule();
}
$("#prev-week").addEventListener("click", () => moveWeek(-7));
$("#next-week").addEventListener("click", () => moveWeek(7));
$("#today-button").addEventListener("click", () => {
  selectedDate = today;
  weekStart = startOfWeek(today);
  refreshSchedule();
});

const views = {
  home: "나의 하루",
  schedule: "나의 일정",
  learning: "나의 학습 자료",
  material: "나의 학습 자료",
  notices: "학교 소식",
  benefits: "복지·취업정보",
};
function setView(view) {
  if (!views[view]) return;
  const navView = view === "material" ? "learning" : view;
  document.querySelectorAll(".nav-button").forEach((button) => {
    const active = button.dataset.view === navView;
    button.classList.toggle("active", active);
    if (active) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  });
  document.querySelectorAll("[data-section]").forEach((section) => {
    section.hidden = !section.dataset.section.split(" ").includes(view);
  });
  $("#dashboard").classList.toggle("focused", view !== "home");
  $("#page-title").textContent = views[view];
  closePopovers();
  renderBotMode();
}
document.addEventListener("click", (event) => {
  const viewButton = event.target.closest("[data-view]");
  if (viewButton) {
    setView(viewButton.dataset.view);
    return;
  }
  const homeLink = event.target.closest('a[href="#home"]');
  if (homeLink) {
    event.preventDefault();
    setView("home");
    window.scrollTo({
      top: 0,
      behavior: motionReduced() ? "instant" : "smooth",
    });
  }
});

const details = {
  class: {
    category: "이번 학기 수업",
    title: "고급웹프로그래밍",
    body: '<p>금요일 18:00 ~ 21:50, 미래관 312호</p><p>이번 주에는 서버 사이드 렌더링과 API 설계를 학습합니다. 수업 전 HTTP 요청과 응답 흐름을 복습해 주세요.</p><div class="detail-note">실제 강의실이나 e-class로 연결되지 않는 수업 정보 예시입니다.</div>',
  },
  career: {
    category: "컴퓨터공학과 3학년 맞춤 취업 추천",
    title: "IT 기업 채용설명회",
    body: '<p>11월 12일 목요일 15:00, 다빈치관</p><p>개발 직무와 채용 과정을 알아보고 현직자에게 포트폴리오 준비 방법을 물어볼 수 있는 프로그램입니다.</p><div class="detail-note">추천 이유: 취업 준비를 시작하는 컴퓨터공학과 3학년에게 적합한 직무 탐색 기회예요.</div><p>이 화면은 목업입니다. 실제 신청이나 개인정보 제출은 진행되지 않습니다.</p>',
  },
  project: {
    category: "전공 역량 비교과 프로그램",
    title: "백엔드 실무 프로젝트 과정",
    body: '<p>11월 17일 화요일 개강, 8주 과정</p><p>팀으로 API를 설계하고 데이터베이스를 구축하며, 테스트와 배포까지 경험하는 실습 과정입니다.</p><div class="detail-note">추천 이유: 데이터베이스와 웹 개발 수업에서 배운 내용을 프로젝트 경험으로 연결할 수 있어요.</div><p>현재 화면에서는 모집 내용만 확인할 수 있으며 실제 신청은 연결되지 않았습니다.</p>',
  },
  software: {
    category: "학생 복지 소프트웨어 혜택",
    title: "학생 소프트웨어 지원",
    body: '<ul><li>JetBrains 학생용 라이선스</li><li>GitHub Student Developer Pack</li></ul><p>학교 이메일 또는 재학 증명이 필요할 수 있으며, 지원 조건과 제공 항목은 각 제공사의 정책에 따라 달라집니다.</p><div class="detail-note">혜택 안내 UI 예시입니다. 재학 인증과 라이선스 발급은 진행하지 않습니다.</div>',
  },
  dorm: {
    category: "생활관 모집 안내",
    title: "2학기 기숙사 추가신청",
    body: '<p>예시 접수 마감: 11월 10일 화요일 18:00</p><p>신청 자격, 공실 현황, 입사 일정은 생활관 공지를 확인해야 합니다.</p><div class="detail-note">실제 모집 공고가 아닙니다. 이 목업에서는 신청과 접수가 이루어지지 않습니다.</div>',
  },
  academic: {
    category: "학사지원과 학사 공지",
    title: "2026학년도 2학기 중간 강의평가 안내",
    body: '<p>수강 중인 교과목에 대한 의견을 남기는 강의평가 안내 화면입니다.</p><p>평가 대상과 참여 기간은 학교의 실제 공지를 확인해 주세요.</p><div class="detail-note">공지 상세보기의 예시이며 실제 평가 화면은 연결되지 않았습니다.</div>',
  },
  scholarship: {
    category: "장학복지팀 장학 공지",
    title: "동계 국가근로장학금 신청 안내",
    body: '<p>동계 방학 중 교내외 근로장학 프로그램의 참여 조건을 확인하는 화면입니다.</p><p>지원 자격, 근로 기간, 선발 일정은 실제 공고에 따라 달라집니다.</p><div class="detail-note">신청 일정과 자격은 이 예시 정보로 판단하지 마세요. 실제 신청 기능은 없습니다.</div>',
  },
  event: {
    category: "중앙도서관 행사 안내",
    title: "가을 독서 챌린지 참여 안내",
    body: '<p>함께 책을 읽고 짧은 독서 기록을 나누는 교내 프로그램 안내입니다.</p><div class="detail-note">학교 소식 화면을 위한 예시로, 실제 행사 등록은 진행되지 않습니다.</div>',
  },
};
const detailDialog = $("#detail-dialog");
function showDetail(key) {
  const detail = details[key];
  if (!detail) return;
  $("#detail-category").textContent = detail.category;
  $("#detail-title").textContent = detail.title;
  $("#detail-body").innerHTML = detail.body;
  detailDialog.showModal();
}
document.addEventListener("click", (event) => {
  const button = event.target.closest("[data-detail]");
  if (button) showDetail(button.dataset.detail);
});
$("#close-detail").addEventListener("click", () => detailDialog.close());
$("#confirm-detail").addEventListener("click", () => detailDialog.close());

const courses = {
  database: {
    name: "데이터베이스",
    meta: ["109351-21001", "김정민", "월(3 ~ 5)"],
    materials: [
      {
        id: "db-10",
        week: 10,
        kind: "PDF",
        title: "관계형 데이터베이스 설계",
        length: "12쪽",
        status: "done",
        outline: [
          ["p.1 ~ 3", "정규화가 필요한 이유와 이상 현상"],
          ["p.4 ~ 6", "함수 종속과 추론 규칙"],
          ["p.7 ~ 9", "1NF부터 BCNF까지"],
          ["p.10 ~ 12", "수강신청 테이블 정규화 예제"],
        ],
        pages: [
          { cover: true, title: "관계형 데이터베이스 설계", lines: ["데이터베이스 10주차 | 김정민"] },
          { title: "정규화가 필요한 이유", lines: ["한 테이블에 여러 주제를 담으면 같은 값이 반복됨", "중복은 저장 공간 낭비와 불일치의 원인", "정규화: 테이블을 나눠 중복을 줄이는 과정"] },
          { title: "이상 현상", lines: ["삽입 이상: 원하지 않는 데이터까지 함께 넣어야 함", "갱신 이상: 중복 값의 일부만 바뀌어 불일치", "삭제 이상: 필요한 데이터까지 함께 지워짐"] },
          { title: "함수 종속", lines: ["X 값이 Y 값을 하나로 정하면 X → Y", "X는 결정자, Y는 종속자", "예: 학번 → 이름"] },
          { title: "완전 · 부분 함수 종속", lines: ["완전: 기본키 전체에 종속 ({학번, 과목코드} → 성적)", "부분: 기본키 일부에만 종속 (학번 → 이름)", "부분 함수 종속은 2NF에서 제거"] },
          { title: "이행적 종속과 암스트롱 공리", lines: ["이행적 종속: X → Y, Y → Z이면 X → Z", "반사: Y가 X의 부분집합이면 X → Y", "첨가: X → Y이면 XZ → YZ", "이행: X → Y, Y → Z이면 X → Z"] },
          { title: "제1정규형 (1NF)", lines: ["모든 속성 값이 원자값", "반복 그룹, 한 칸에 여러 값 금지", "예: 전화번호 여러 개를 한 칸에 넣지 않음"] },
          { title: "제2 · 제3정규형", lines: ["2NF: 1NF이고 부분 함수 종속 없음", "3NF: 2NF이고 이행적 종속 없음", "기본키가 아닌 속성끼리의 종속을 분리"] },
          { title: "BCNF", lines: ["모든 결정자가 슈퍼키", "3NF와 차이: 후보키가 여러 개이고 서로 겹칠 때", "대부분의 3NF 테이블은 BCNF도 만족"] },
          { title: "예제: 수강신청 테이블", lines: ["수강신청(학번, 이름, 학과, 학과 사무실, 과목코드, 과목명, 성적)", "기본키: {학번, 과목코드}", "이름·학과는 학번에만, 과목명은 과목코드에만 종속"] },
          { title: "2NF로 분해", lines: ["학생(학번, 이름, 학과, 학과 사무실)", "과목(과목코드, 과목명)", "수강(학번, 과목코드, 성적)"] },
          { title: "3NF로 분해", lines: ["학번 → 학과 → 학과 사무실 종속을 분리", "학생(학번, 이름, 학과), 학과(학과, 학과 사무실)", "과목(과목코드, 과목명), 수강(학번, 과목코드, 성적)"] },
        ],
        summary: {
          lead: "정규화는 테이블을 나눠 데이터 중복을 줄이고 삽입, 갱신, 삭제 이상을 막는 과정입니다. 이번 자료는 이상 현상, 함수 종속, 정규형, 예제 순서로 설명합니다.",
          sections: [
            {
              lead: "한 테이블에 여러 주제의 데이터를 몰아 담으면 같은 값이 여러 행에 반복되고, 이 중복 때문에 세 가지 이상 현상이 생깁니다.",
              table: [
                ["이상 현상", "발생 상황", "수강신청 테이블 예시"],
                ["삽입 이상", "원하지 않는 데이터까지 함께 넣어야 함", "수강 과목이 없는 신입생을 등록할 수 없음"],
                ["갱신 이상", "중복된 값의 일부만 바뀌어 불일치가 생김", "학과 사무실을 한 행에서만 고침"],
                ["삭제 이상", "필요한 데이터까지 함께 지워짐", "마지막 수강 기록을 지우면 학생 정보도 사라짐"],
              ],
              points: [["핵심", "이상 현상의 원인은 중복이고, 중복은 잘못 묶인 함수 종속에서 생깁니다."]],
            },
            {
              lead: "X 값이 정해지면 Y 값이 하나로 정해질 때 Y는 X에 함수 종속(X → Y)이라고 합니다. 모든 정규형은 이 종속 관계로 정의됩니다.",
              points: [
                ["완전 함수 종속", "Y가 기본키 전체에 종속됩니다. 예: {학번, 과목코드} → 성적"],
                ["부분 함수 종속", "Y가 기본키 일부에만 종속됩니다. 예: {학번, 과목코드} 중 학번 → 이름"],
                ["이행적 종속", "X → Y, Y → Z이면 X → Z가 됩니다. 예: 학번 → 학과, 학과 → 학과 사무실"],
                [
                  "암스트롱 공리",
                  "주어진 종속에서 새 종속을 이끌어내는 세 규칙입니다.",
                  [
                    ["반사", "Y가 X의 부분집합이면 X → Y"],
                    ["첨가", "X → Y이면 XZ → YZ"],
                    ["이행", "X → Y이고 Y → Z이면 X → Z"],
                  ],
                ],
              ],
            },
            {
              lead: "정규형은 단계가 올라갈수록 조건이 엄격해지고, 앞 단계를 만족해야 다음 단계로 갈 수 있습니다.",
              table: [
                ["정규형", "조건", "없애는 것"],
                ["1NF", "모든 속성이 원자값", "반복 그룹, 한 칸에 여러 값"],
                ["2NF", "1NF이고 부분 함수 종속이 없음", "기본키 일부에 대한 종속"],
                ["3NF", "2NF이고 이행적 종속이 없음", "기본키가 아닌 속성 사이의 종속"],
                ["BCNF", "모든 결정자가 슈퍼키", "후보키가 아닌 결정자"],
              ],
              points: [["3NF와 BCNF", "대부분의 테이블은 3NF면 BCNF도 만족합니다. 후보키가 여러 개이고 서로 겹칠 때만 차이가 납니다."]],
            },
            {
              lead: "수강신청(학번, 이름, 학과, 학과 사무실, 과목코드, 과목명, 성적) 테이블을 단계별로 나눕니다.",
              points: [
                ["2NF로", "이름과 학과는 학번에만, 과목명은 과목코드에만 종속되므로 학생, 과목, 수강 테이블로 나눕니다."],
                ["3NF로", "학번 → 학과 → 학과 사무실로 이어지는 종속을 끊기 위해 학과 테이블을 따로 둡니다."],
                [
                  "결과",
                  "네 테이블이 됩니다.",
                  [
                    ["학생", "학번, 이름, 학과"],
                    ["학과", "학과, 학과 사무실"],
                    ["과목", "과목코드, 과목명"],
                    ["수강", "학번, 과목코드, 성적"],
                  ],
                ],
              ],
            },
          ],
        },
        concepts: [
          ["함수 종속", "X 값이 정해지면 Y 값이 하나로 정해지는 관계(X → Y)", "p.4"],
          ["부분 함수 종속", "기본키의 일부에만 종속되는 속성이 있는 상태", "p.5"],
          ["이행적 종속", "X → Y, Y → Z가 성립해 X → Z로 이어지는 종속", "p.6"],
          ["슈퍼키", "튜플을 유일하게 식별하는 속성의 집합", "p.9"],
        ],
        quiz: [
          {
            q: "기본키가 {학번, 과목코드}이고 학생 이름이 학번에만 종속될 때 위반하는 정규형은?",
            options: ["1NF", "2NF", "3NF", "BCNF"],
            answer: 1,
            explain: "기본키 일부(학번)에만 종속되는 부분 함수 종속이라 2NF를 위반합니다. (p.5)",
          },
          {
            q: "X → Y, Y → Z가 성립해 X → Z가 되는 종속은?",
            options: ["완전 함수 종속", "다치 종속", "이행적 종속", "조인 종속"],
            answer: 2,
            explain: "이행적 종속이며 3NF에서 제거합니다. (p.6)",
          },
          {
            q: "BCNF의 조건으로 옳은 것은?",
            options: ["모든 속성이 원자값이다", "모든 결정자가 슈퍼키다", "기본키가 속성 하나다", "외래키가 없다"],
            answer: 1,
            explain: "모든 비자명한 함수 종속의 결정자가 슈퍼키여야 합니다. (p.9)",
          },
        ],
        questions: [
          ["3NF와 BCNF는 어떻게 달라요?", "3NF는 결정자가 슈퍼키가 아니어도 종속되는 쪽이 주요 속성이면 허용합니다. BCNF는 모든 결정자가 슈퍼키여야 해서 더 엄격합니다.", "p.8 ~ 9"],
          ["정규화 예제를 다시 설명해 줘", "수강신청 테이블을 학생, 과목, 수강 세 테이블로 나눕니다. 학생 이름의 부분 종속은 학생 테이블로, 학과 사무실의 이행적 종속은 학과 테이블로 분리합니다.", "p.10 ~ 12"],
          ["먼저 복습할 부분은?", "정규형별 조건과 위반 사례 구분이 이번 자료의 중심이에요. p.7 ~ 9의 정규형 설명부터 보세요.", "p.7 ~ 9"],
        ],
      },
      {
        id: "db-11",
        week: 11,
        kind: "PDF",
        title: "트랜잭션과 동시성 제어",
        length: "28쪽",
        status: "pending",
      },
    ],
  },
  os: {
    name: "운영체제",
    meta: ["109347-11001", "박서준", "수(1 ~ 3)"],
    materials: [
      {
        id: "os-10",
        week: 10,
        kind: "영상",
        title: "프로세스와 스레드",
        length: "42:00",
        status: "done",
        outline: [
          ["00:00", "프로세스의 구성과 상태 전이"],
          ["08:30", "문맥 교환과 스케줄러"],
          ["17:40", "스레드의 구조와 자원 공유"],
          ["25:10", "동시성과 병렬성의 차이"],
          ["33:00", "경쟁 상태와 동기화"],
          ["39:00", "핵심 개념 정리"],
        ],
        summary: {
          lead: "프로세스는 실행 중인 프로그램이고, 스레드는 프로세스 안의 실행 흐름입니다. 이번 강의는 상태 전이와 문맥 교환을 거쳐 스레드, 동시성, 동기화까지 다룹니다.",
          sections: [
            {
              lead: "프로세스는 코드, 데이터, 힙, 스택 영역으로 된 독립적인 주소 공간을 가지고, 실행되는 동안 다섯 가지 상태를 오갑니다.",
              points: [
                [
                  "상태 전이",
                  "생성, 준비, 실행, 대기, 종료 사이를 옮겨 다닙니다.",
                  [
                    ["준비 → 실행", "스케줄러가 CPU를 배정(디스패치)"],
                    ["실행 → 대기", "입출력 요청처럼 기다릴 일이 생김"],
                    ["실행 → 준비", "타이머 인터럽트로 CPU를 내줌"],
                    ["대기 → 준비", "기다리던 입출력이 끝남"],
                  ],
                ],
              ],
            },
            {
              lead: "CPU가 다른 프로세스로 넘어갈 때 현재 상태를 PCB에 저장하고 다음 프로세스의 상태를 불러오는 일을 문맥 교환이라 합니다.",
              points: [
                ["PCB", "프로세스 번호, 상태, 프로그램 카운터, 레지스터, 메모리 정보를 담습니다."],
                ["스케줄러", "준비 상태의 프로세스 중 다음에 CPU를 쓸 프로세스를 고릅니다."],
                ["비용", "문맥 교환 중에는 실제 작업을 하지 못하므로 너무 잦으면 성능이 떨어집니다."],
              ],
            },
            {
              lead: "같은 프로세스의 스레드는 주소 공간을 공유하고, 실행에 꼭 필요한 것만 따로 가집니다.",
              table: [
                ["구분", "스레드끼리 공유", "스레드마다 따로"],
                ["메모리", "코드, 데이터, 힙", "스택"],
                ["실행 상태", "없음", "프로그램 카운터, 레지스터"],
                ["운영체제 자원", "열린 파일, 신호 처리기", "없음"],
              ],
              points: [["장점", "스레드 전환은 주소 공간을 바꾸지 않아 프로세스 전환보다 가볍습니다."]],
            },
            {
              lead: "둘 다 여러 작업을 다루는 방식이지만, 실제로 같은 순간에 실행되는지가 다릅니다.",
              points: [
                ["동시성", "코어 하나가 여러 작업을 빠르게 번갈아 실행해 동시에 진행되는 것처럼 보입니다."],
                ["병렬성", "여러 코어가 여러 작업을 같은 순간에 실제로 실행합니다."],
              ],
            },
            {
              lead: "여러 스레드가 공유 변수를 동시에 고치면 실행 순서에 따라 결과가 달라지는 경쟁 상태가 생깁니다.",
              points: [
                ["임계 구역", "공유 자원에 접근하는 코드 구간입니다. 한 번에 한 스레드만 들어가야 합니다."],
                [
                  "해결 조건",
                  "세 조건을 모두 만족해야 합니다.",
                  [
                    ["상호 배제", "한 스레드가 들어가 있으면 다른 스레드는 못 들어감"],
                    ["진행", "아무도 없으면 기다리던 스레드가 들어갈 수 있음"],
                    ["한정 대기", "무한히 기다리는 스레드가 없음"],
                  ],
                ],
              ],
            },
            {
              lead: "강의 끝에 정리한 프로세스와 스레드 비교입니다.",
              table: [
                ["항목", "프로세스", "스레드"],
                ["주소 공간", "독립", "공유"],
                ["생성과 전환 비용", "큼", "작음"],
                ["한쪽 오류의 영향", "다른 프로세스에 거의 없음", "같은 프로세스 전체가 멈출 수 있음"],
              ],
            },
          ],
        },
        concepts: [
          ["PCB", "프로세스의 상태, 레지스터, 메모리 정보를 담은 운영체제의 자료 구조", "08:30"],
          ["문맥 교환", "실행 중인 프로세스의 상태를 저장하고 다른 프로세스의 상태를 불러오는 일", "08:30"],
          ["스레드", "프로세스 안에서 실행되는 흐름. 스택과 레지스터만 따로 가짐", "17:40"],
          ["경쟁 상태", "여러 실행 흐름이 공유 자원에 접근하는 순서에 따라 결과가 달라지는 상황", "33:00"],
        ],
        quiz: [
          {
            q: "같은 프로세스의 스레드끼리 공유하지 않는 것은?",
            options: ["코드 영역", "전역 변수", "스택", "열린 파일"],
            answer: 2,
            explain: "스택과 레지스터는 스레드마다 따로 가집니다. (17:40)",
          },
          {
            q: "문맥 교환 때 프로세스 상태를 저장하는 곳은?",
            options: ["PCB", "TLB", "페이지 테이블", "캐시"],
            answer: 0,
            explain: "레지스터와 상태 정보는 PCB에 저장했다가 복원합니다. (08:30)",
          },
          {
            q: "실행 순서에 따라 결과가 달라지는 상황을 막으려면?",
            options: ["스레드 수 줄이기", "동기화", "문맥 교환 늘리기", "우선순위 높이기"],
            answer: 1,
            explain: "임계 구역에 한 흐름만 들어가도록 동기화해야 합니다. (33:00)",
          },
        ],
        questions: [
          ["스레드 전환이 왜 더 가벼워요?", "같은 주소 공간을 쓰기 때문에 페이지 테이블과 캐시를 바꿀 필요가 적습니다. 스택 포인터와 레지스터만 바꾸면 됩니다.", "17:40 ~ 25:10"],
          ["동시성과 병렬성 차이는?", "동시성은 하나의 코어에서 여러 작업을 번갈아 실행하는 것이고, 병렬성은 여러 코어에서 실제로 같은 시각에 실행하는 것입니다.", "25:10 ~ 33:00"],
          ["10분 안에 복습하려면 어디부터?", "39:00의 핵심 개념 정리를 먼저 보고, 헷갈리는 부분만 해당 구간으로 돌아가세요.", "39:00 ~ 42:00"],
        ],
      },
      {
        id: "os-9",
        week: 9,
        kind: "영상",
        title: "CPU 스케줄링",
        length: "45:00",
        status: "failed",
      },
    ],
  },
  web: {
    name: "고급웹프로그래밍",
    meta: ["109412-31001", "이도현", "금(10 ~ 13)"],
    materials: [
      {
        id: "web-10",
        week: 10,
        kind: "PDF",
        title: "서버 사이드 렌더링과 API 설계",
        length: "10쪽",
        status: "done",
        outline: [
          ["p.1 ~ 3", "HTTP 요청과 응답 흐름"],
          ["p.4 ~ 5", "CSR과 SSR 비교"],
          ["p.6 ~ 8", "REST API 자원과 메서드 설계"],
          ["p.9 ~ 10", "오류 응답과 상태 코드"],
        ],
        pages: [
          { cover: true, title: "서버 사이드 렌더링과 API 설계", lines: ["고급웹프로그래밍 10주차 | 이도현"] },
          { title: "HTTP 요청과 응답", lines: ["브라우저 → 서버: 메서드, 주소, 헤더, 본문", "서버 → 브라우저: 상태 코드, 헤더, 본문", "요청 하나에 응답 하나"] },
          { title: "메시지 구조", lines: ["요청 줄: GET /courses/101 HTTP/1.1", "응답 줄: HTTP/1.1 200 OK", "헤더: Content-Type, Cache-Control"] },
          { title: "CSR과 SSR", lines: ["CSR: 브라우저가 스크립트로 HTML을 완성", "SSR: 서버가 요청마다 HTML을 완성", "SSR은 첫 화면이 빠르고 검색 엔진이 읽기 쉬움"] },
          { title: "하이드레이션", lines: ["SSR로 받은 HTML에 이벤트와 상태를 연결", "첫 화면은 서버, 상호작용은 브라우저", "대가: 요청마다 렌더링하는 서버 부담"] },
          { title: "REST 자원 설계", lines: ["주소는 자원을 나타내는 명사", "복수형 사용: /courses", "계층은 슬래시: /courses/101/students"] },
          { title: "HTTP 메서드와 멱등성", lines: ["GET 조회, POST 생성, PUT 전체 수정, DELETE 삭제", "멱등: 여러 번 보내도 결과가 같음", "GET, PUT, DELETE는 멱등, POST는 아님"] },
          { title: "PUT과 PATCH", lines: ["PUT: 자원 전체를 바꿈", "PATCH: 일부 필드만 바꿈", "PATCH는 멱등성이 보장되지 않음"] },
          { title: "상태 코드", lines: ["400 잘못된 요청, 401 인증 필요, 403 권한 없음", "404 자원 없음, 409 상태 충돌", "500 서버 내부 오류"] },
          { title: "오류 응답 본문", lines: ["상태 코드와 함께 원인을 본문에 담음", "예: code DUPLICATE_ID", "예: message 이미 사용 중인 아이디입니다."] },
        ],
        summary: {
          lead: "서버 사이드 렌더링(SSR)과 REST API 설계를 다룹니다. HTTP 흐름을 먼저 짚고 렌더링 방식을 비교한 뒤, 자원 중심 API와 오류 응답 규칙으로 이어집니다.",
          sections: [
            {
              lead: "브라우저가 메서드, 주소, 헤더, 본문으로 요청을 보내면 서버는 상태 코드, 헤더, 본문으로 응답합니다.",
              points: [
                ["요청 줄", "GET /courses/101 HTTP/1.1"],
                ["응답 줄", "HTTP/1.1 200 OK"],
                ["헤더", "Content-Type, Cache-Control처럼 본문을 어떻게 다룰지 알려주는 정보입니다."],
              ],
            },
            {
              lead: "HTML을 어디서 완성하느냐에 따라 첫 화면 속도와 서버 부담이 달라집니다.",
              table: [
                ["구분", "CSR", "SSR"],
                ["HTML을 완성하는 곳", "브라우저", "서버"],
                ["첫 화면 표시", "스크립트 실행 뒤라 느림", "빠름"],
                ["검색 엔진", "내용을 읽기 어려울 수 있음", "읽기 쉬움"],
                ["서버 부담", "적음", "요청마다 렌더링해 큼"],
              ],
              points: [["하이드레이션", "SSR로 받은 HTML에 브라우저가 이벤트와 상태를 연결해 상호작용을 붙입니다."]],
            },
            {
              lead: "주소는 자원을 명사로 나타내고, 동작은 HTTP 메서드로 구분합니다.",
              table: [
                ["메서드", "용도", "예시", "멱등"],
                ["GET", "조회", "GET /courses", "예"],
                ["POST", "생성", "POST /courses", "아니오"],
                ["PUT", "전체 수정", "PUT /courses/101", "예"],
                ["PATCH", "일부 수정", "PATCH /courses/101", "보장 안 됨"],
                ["DELETE", "삭제", "DELETE /courses/101", "예"],
              ],
              points: [["주소 규칙", "동사 대신 복수형 명사를 쓰고 계층은 슬래시로 나타냅니다. 예: /courses/101/students"]],
            },
            {
              lead: "오류는 상태 코드로 종류를 알리고, 본문에 원인을 담아 응답합니다.",
              points: [
                ["400", "요청 형식이 잘못됨"],
                ["401, 403", "인증되지 않음, 권한 없음"],
                ["404", "자원이 없음"],
                ["409", "현재 상태와 충돌함. 예: 이미 있는 아이디로 가입"],
                ["500", "서버 내부 오류"],
                ["본문", "code, message 같은 필드로 원인을 알 수 있게 응답합니다."],
              ],
            },
          ],
        },
        concepts: [
          ["SSR", "서버가 요청마다 HTML을 완성해 보내는 렌더링 방식", "p.4"],
          ["하이드레이션", "서버가 보낸 HTML에 브라우저가 이벤트와 상태를 연결하는 과정", "p.5"],
          ["멱등성", "같은 요청을 여러 번 보내도 결과가 같은 성질. GET, PUT, DELETE가 해당", "p.7"],
          ["404와 409", "자원이 없음(404)과 현재 상태와 충돌함(409)", "p.9"],
        ],
        quiz: [
          {
            q: "첫 화면이 가장 빨리 보이는 방식은?",
            options: ["CSR", "SSR", "둘 다 같음", "SPA"],
            answer: 1,
            explain: "SSR은 완성된 HTML을 바로 받아 표시합니다. (p.4)",
          },
          {
            q: "멱등하지 않은 HTTP 메서드는?",
            options: ["GET", "PUT", "DELETE", "POST"],
            answer: 3,
            explain: "POST는 보낼 때마다 새 자원을 만들 수 있어 멱등하지 않습니다. (p.7)",
          },
          {
            q: "이미 있는 아이디로 회원가입할 때 알맞은 상태 코드는?",
            options: ["200", "404", "409", "500"],
            answer: 2,
            explain: "현재 자원 상태와 충돌하므로 409 Conflict가 알맞습니다. (p.9)",
          },
        ],
        questions: [
          ["SSR은 언제 쓰면 좋아요?", "첫 화면 속도와 검색 노출이 중요한 공개 페이지에 알맞습니다. 로그인 뒤 대시보드처럼 상호작용이 많은 화면은 CSR도 충분합니다.", "p.4 ~ 5"],
          ["PUT과 PATCH 차이는?", "PUT은 자원 전체를 바꾸고 PATCH는 일부만 바꿉니다. 일부 필드만 고칠 때는 PATCH가 알맞습니다.", "p.8"],
          ["이번 주 과제와 겹치는 부분은?", "REST API 설계 과제와 겹치는 부분은 p.6 ~ 8의 자원 이름과 메서드 설명입니다.", "p.6 ~ 8"],
        ],
      },
    ],
  },
};
const materialStatus = { done: "요약 완료", pending: "요약 중", failed: "요약 실패" };
const materialTabs = [
  ["summary", "요약"],
  ["concepts", "핵심 개념"],
  ["quiz", "퀴즈"],
  ["cards", "플래시카드"],
];
const zoomSteps = [60, 80, 100];
let currentCourse = "database",
  currentMaterial = "db-10",
  currentTab = "summary",
  activePart = 0,
  currentPage = 1,
  zoomIndex = zoomSteps.length - 1,
  quizAnswers = {},
  cardIndex = 0,
  cardFlipped = false,
  studyThread = [],
  pageFrame,
  pageLockUntil = 0;
function findMaterial() {
  return courses[currentCourse].materials.find(
    (material) => material.id === currentMaterial,
  );
}
function materialLabel(material) {
  return `${courses[currentCourse].name} ${material.week}주차 ${material.kind}`;
}
function selectMaterial(id) {
  currentMaterial = id;
  currentTab = "summary";
  activePart = 0;
  currentPage = 1;
  quizAnswers = {};
  cardIndex = 0;
  cardFlipped = false;
  studyThread = [];
}
function openMaterial(courseKey, materialId) {
  if (!courses[courseKey]) return;
  currentCourse = courseKey;
  selectMaterial(materialId || courses[courseKey].materials[0].id);
  setView("material");
  renderMaterial();
  window.scrollTo({ top: 0, behavior: motionReduced() ? "instant" : "smooth" });
}
function renderMaterial() {
  const course = courses[currentCourse];
  $("#material-title").textContent = course.name;
  $("#material-course-meta").innerHTML = course.meta
    .map((part) => `<span>${part}</span>`)
    .join("");
  renderSource();
  renderResult();
  renderBotMode();
}
function pageStart(at) {
  return Number(at.match(/\d+/)[0]);
}
function sectionOfPage(material, page) {
  let section = 0;
  material.outline.forEach(([at], index) => {
    if (pageStart(at) <= page) section = index;
  });
  return section;
}
const chevron = (path) =>
  `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`;
function renderSource() {
  const material = findMaterial();
  const course = courses[currentCourse];
  const picker = `<label class="viewer-picker"><span class="visually-hidden">자료 선택</span><select id="material-select">${course.materials
    .map(
      (item) =>
        `<option value="${item.id}"${item.id === currentMaterial ? " selected" : ""}>${item.week}주차 ${item.kind} | ${item.title} (${materialStatus[item.status]})</option>`,
    )
    .join("")}</select></label>`;
  const link = `<a class="viewer-link" href="https://eclass.seoultech.ac.kr/ilos/main/main_form.acl" target="_blank" rel="noopener noreferrer">e-class 원본<span aria-hidden="true">↗</span></a>`;
  let tools = "";
  let body;
  if (material.pages) {
    const total = material.pages.length;
    tools = `<div class="viewer-group"><button class="icon-button" data-page-step="-1" aria-label="이전 쪽"${currentPage === 1 ? " disabled" : ""}>${chevron("m10 3.5-4.5 4.5 4.5 4.5")}</button><span class="viewer-count" id="viewer-page">${currentPage} / ${total}</span><button class="icon-button" data-page-step="1" aria-label="다음 쪽"${currentPage === total ? " disabled" : ""}>${chevron("m6 3.5 4.5 4.5L6 12.5")}</button></div><div class="viewer-group"><button class="icon-button" data-zoom="-1" aria-label="축소"${zoomIndex === 0 ? " disabled" : ""}>${chevron("M3.5 8h9")}</button><span class="viewer-count" id="viewer-zoom">${zoomSteps[zoomIndex]}%</span><button class="icon-button" data-zoom="1" aria-label="확대"${zoomIndex === zoomSteps.length - 1 ? " disabled" : ""}>${chevron("M3.5 8h9M8 3.5v9")}</button></div>`;
    body = `<div class="pdf-pages" id="pdf-pages" tabindex="0" aria-label="${material.title} PDF 미리보기" style="--zoom: ${zoomSteps[zoomIndex] / 100}">${material.pages
      .map((page, index) => {
        const number = index + 1;
        const lines = page.cover
          ? page.lines.map((line) => `<p>${line}</p>`).join("")
          : `<ul>${page.lines.map((line) => `<li>${line}</li>`).join("")}</ul><div class="pdf-footer"><span>${course.name} ${material.week}주차 | ${material.title}</span><span>${number}</span></div>`;
        return `<article class="pdf-page${page.cover ? " cover" : ""}" id="pdf-page-${number}" data-page="${number}" aria-label="${number}쪽"><div class="pdf-sheet"><p class="pdf-title">${page.title}</p>${lines}</div></article>`;
      })
      .join("")}</div>`;
  } else if (material.kind === "영상") {
    const position = material.outline
      ? `재생 위치 ${material.outline[activePart][0]} / ${material.length}`
      : `전체 ${material.length}`;
    const outline = material.outline
      ? `<h3>자막 구간</h3><ol class="source-outline">${material.outline
          .map(
            ([at, label], index) =>
              `<li><button data-part="${index}"${index === activePart ? ' aria-current="true"' : ""}><span>${at}</span>${label}</button></li>`,
          )
          .join("")}</ol>`
      : "";
    body = `<div class="viewer-body"><div class="source-frame video"><svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="16" cy="16" r="13"/><path d="m13 10.5 8 5.5-8 5.5z" fill="currentColor"/></svg><strong>${material.title}</strong><span>${position}</span></div>${outline}</div>`;
  } else {
    body = `<div class="viewer-body"><div class="source-frame"><svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M8 3.5h11l6 6v19H8z"/><path d="M19 3.5v6h6M12 16h9M12 21h9"/></svg><strong>${material.title}</strong><span>전체 ${material.length} | 미리보기는 요약이 끝난 뒤 열려요</span></div></div>`;
  }
  $("#material-source").innerHTML = `<div class="viewer-toolbar">${picker}${tools}${link}</div>${body}`;
  const pages = $("#pdf-pages");
  if (!pages) return;
  pages.addEventListener("scroll", trackPage, { passive: true });
  if (currentPage > 1) scrollToPage(currentPage, "instant");
}
function scrollToPage(page, behavior) {
  const pages = $("#pdf-pages");
  pageLockUntil = performance.now() + (behavior === "smooth" ? 800 : 100);
  pages.scrollTo({ top: $(`#pdf-page-${page}`).offsetTop - 16, behavior });
}
function trackPage() {
  cancelAnimationFrame(pageFrame);
  pageFrame = requestAnimationFrame(() => {
    const pages = $("#pdf-pages");
    if (!pages || performance.now() < pageLockUntil) return;
    const mark = pages.getBoundingClientRect().top + 48;
    let page = 1;
    for (const node of pages.querySelectorAll(".pdf-page"))
      if (node.getBoundingClientRect().top <= mark) page = Number(node.dataset.page);
    const atBottom = pages.scrollTop + pages.clientHeight >= pages.scrollHeight - 4;
    if (page !== currentPage && !(atBottom && page < currentPage)) setCurrentPage(page, false);
  });
}
function setCurrentPage(page, scroll) {
  const material = findMaterial();
  const total = material.pages.length;
  currentPage = Math.min(Math.max(page, 1), total);
  $("#viewer-page").textContent = `${currentPage} / ${total}`;
  $('[data-page-step="-1"]').disabled = currentPage === 1;
  $('[data-page-step="1"]').disabled = currentPage === total;
  if (scroll) scrollToPage(currentPage, motionReduced() ? "instant" : "smooth");
  const section = sectionOfPage(material, currentPage);
  if (section !== activePart) {
    activePart = section;
    markSummarySection(false);
  }
}
function renderResult() {
  const material = findMaterial();
  const result = $("#material-result");
  if (material.status === "pending") {
    result.innerHTML = `<div class="result-state"><strong>요약을 만드는 중이에요</strong><p>e-class에 올라온 ${material.length} 자료입니다. 요약이 끝나면 알림으로 알려드려요.</p></div>`;
    return;
  }
  if (material.status === "failed") {
    result.innerHTML = `<div class="result-state failed"><strong>요약을 만들지 못했어요</strong><p>강의 영상의 음성을 인식하지 못했습니다. 원본은 e-class에서 볼 수 있어요.</p><button class="small-button" data-retry="${material.id}">다시 요약하기</button></div>`;
    return;
  }
  result.innerHTML = `<div class="filter-tabs result-tabs" role="tablist" aria-label="학습 도구">${materialTabs
    .map(([key, label]) => {
      const selected = key === currentTab;
      return `<button role="tab" id="tab-${key}" class="${selected ? "selected" : ""}" aria-selected="${selected}" aria-controls="result-panel" tabindex="${selected ? 0 : -1}" data-tab="${key}">${label}</button>`;
    })
    .join("")}</div><div class="result-panel" id="result-panel" role="tabpanel" aria-labelledby="tab-${currentTab}" tabindex="0">${renderTab(material)}</div><button class="material-ask" data-ask><img src="assets/technyong-front.webp" alt="" width="20" height="28">이 자료로 테크뇽에게 질문하기</button>`;
}
function renderTab(material) {
  if (currentTab === "summary")
    return `<p class="summary-lead">${material.summary.lead}</p>${material.summary.sections
      .map((section, index) => {
        const [at, title] = material.outline[index];
        const current = index === activePart;
        const [head, ...rows] = section.table || [];
        const table = section.table
          ? `<div class="summary-table"><table><thead><tr>${head.map((cell) => `<th scope="col">${cell}</th>`).join("")}</tr></thead><tbody>${rows
              .map((row) => `<tr>${row.map((cell) => `<td>${cell}</td>`).join("")}</tr>`)
              .join("")}</tbody></table></div>`
          : "";
        const points = section.points
          ? `<ul>${section.points
              .map(
                ([label, text, sub]) =>
                  `<li><strong>${label}</strong>: ${text}${sub ? `<ul>${sub.map(([subLabel, subText]) => `<li><strong>${subLabel}</strong>: ${subText}</li>`).join("")}</ul>` : ""}</li>`,
              )
              .join("")}</ul>`
          : "";
        return `<section class="summary-section${current ? " current" : ""}" id="summary-${index}" aria-labelledby="summary-title-${index}"><div class="summary-head"><h3 id="summary-title-${index}">${title}</h3><button class="summary-jump" data-jump="${index}" aria-label="원본 ${at} 위치 보기"${current ? ' aria-current="true"' : ""}>${at}</button></div><p>${section.lead}</p>${table}${points}</section>`;
      })
      .join("")}<p class="result-source">원본 ${material.length} 기준 예시 요약입니다.</p>`;
  if (currentTab === "concepts")
    return `<dl class="concept-list">${material.concepts
      .map(([term, meaning, at]) => `<div><dt>${term}</dt><dd>${meaning}<span>${at}</span></dd></div>`)
      .join("")}</dl>`;
  if (currentTab === "quiz") {
    const answered = Object.keys(quizAnswers).length;
    const correct = material.quiz.filter((item, index) => quizAnswers[index] === item.answer).length;
    return `<div class="quiz-progress"><span>${material.quiz.length}문제 중 ${answered}문제 풀었어요${answered ? ` | 맞힌 문제 ${correct}` : ""}</span>${answered ? '<button class="text-button" data-quiz-reset>다시 풀기</button>' : ""}</div><ol class="quiz-list">${material.quiz
      .map((item, index) => {
        const chosen = quizAnswers[index];
        const done = chosen !== undefined;
        const options = item.options
          .map((option, choice) => {
            let state = "";
            if (done && choice === item.answer) state = "correct";
            else if (done && choice === chosen) state = "wrong";
            return `<button class="${state}" data-quiz="${index}" data-choice="${choice}"${done ? ' aria-disabled="true"' : ""}>${option}</button>`;
          })
          .join("");
        const explain = done
          ? `<p class="quiz-explain ${chosen === item.answer ? "correct" : "wrong"}" id="quiz-explain-${index}" tabindex="-1"><strong>${chosen === item.answer ? "정답" : "오답"}</strong>${item.explain}</p>`
          : "";
        return `<li><p class="quiz-question">${index + 1}. ${item.q}</p><div class="quiz-options">${options}</div>${explain}</li>`;
      })
      .join("")}</ol>`;
  }
  const [term, meaning, at] = material.concepts[cardIndex];
  return `<div class="flashcard${cardFlipped ? " back" : ""}"><span>${cardIndex + 1} / ${material.concepts.length}</span><strong>${cardFlipped ? meaning : term}</strong><small>${cardFlipped ? `뜻 | ${at}` : "용어"}</small></div><div class="flashcard-actions"><button class="small-button" data-card="prev"${cardIndex === 0 ? " disabled" : ""}>이전</button><button class="primary-button" data-card="flip">${cardFlipped ? "용어 보기" : "뜻 보기"}</button><button class="small-button" data-card="next"${cardIndex === material.concepts.length - 1 ? " disabled" : ""}>다음</button></div>`;
}
function refreshTab(focusSelector) {
  $("#result-panel").innerHTML = renderTab(findMaterial());
  if (focusSelector) $(focusSelector)?.focus({ preventScroll: true });
}
function markSummarySection(scroll) {
  if (currentTab !== "summary") return;
  document.querySelectorAll(".summary-section").forEach((section, index) => {
    const current = index === activePart;
    section.classList.toggle("current", current);
    const jump = section.querySelector("[data-jump]");
    if (current) jump.setAttribute("aria-current", "true");
    else jump.removeAttribute("aria-current");
  });
  if (!scroll) return;
  const section = $(`#summary-${activePart}`);
  const top = section.getBoundingClientRect().top;
  if (top < 0 || top > window.innerHeight - 120)
    section.scrollIntoView({ block: "start", behavior: motionReduced() ? "instant" : "smooth" });
}
$(".material-panel").addEventListener("click", (event) => {
  const jump = event.target.closest("[data-jump]");
  if (jump) {
    const material = findMaterial();
    activePart = Number(jump.dataset.jump);
    if (material.pages) setCurrentPage(pageStart(material.outline[activePart][0]), true);
    else renderSource();
    markSummarySection(false);
    jump.focus({ preventScroll: true });
    return;
  }
  const part = event.target.closest("[data-part]");
  if (part) {
    activePart = Number(part.dataset.part);
    renderSource();
    markSummarySection(true);
    $(`[data-part="${activePart}"]`).focus({ preventScroll: true });
    return;
  }
  const step = event.target.closest("[data-page-step]");
  if (step) {
    setCurrentPage(currentPage + Number(step.dataset.pageStep), true);
    if (step.disabled) $(`[data-page-step="${-Number(step.dataset.pageStep)}"]`).focus({ preventScroll: true });
    return;
  }
  const zoom = event.target.closest("[data-zoom]");
  if (zoom) {
    const direction = zoom.dataset.zoom;
    zoomIndex = Math.min(Math.max(zoomIndex + Number(direction), 0), zoomSteps.length - 1);
    const page = currentPage;
    renderSource();
    scrollToPage(page, "instant");
    currentPage = page;
    const target = $(`[data-zoom="${direction}"]`);
    (target.disabled ? $(`[data-zoom="${-Number(direction)}"]`) : target).focus({ preventScroll: true });
    return;
  }
  const tab = event.target.closest("[data-tab]");
  if (tab) {
    currentTab = tab.dataset.tab;
    renderResult();
    $(`#tab-${currentTab}`).focus({ preventScroll: true });
    return;
  }
  const option = event.target.closest("[data-quiz]");
  if (option) {
    const index = Number(option.dataset.quiz);
    if (quizAnswers[index] !== undefined) return;
    quizAnswers[index] = Number(option.dataset.choice);
    refreshTab(`#quiz-explain-${index}`);
    return;
  }
  if (event.target.closest("[data-quiz-reset]")) {
    quizAnswers = {};
    refreshTab('[data-quiz="0"]');
    return;
  }
  const card = event.target.closest("[data-card]");
  if (card) {
    const action = card.dataset.card;
    if (action === "flip") cardFlipped = !cardFlipped;
    else {
      cardIndex += action === "next" ? 1 : -1;
      cardFlipped = false;
    }
    refreshTab();
    const target = $(`[data-card="${action}"]`);
    (target.disabled ? $('[data-card="flip"]') : target).focus({ preventScroll: true });
    return;
  }
  const retry = event.target.closest("[data-retry]");
  if (retry) {
    findMaterial().status = "pending";
    renderMaterial();
    $("#material-select").focus({ preventScroll: true });
    showToast("다시 요약을 요청했어요. 끝나면 알림으로 알려드려요.");
    return;
  }
  if (event.target.closest("[data-ask]")) toggleBot(true);
});
$(".material-panel").addEventListener("change", (event) => {
  if (event.target.id !== "material-select") return;
  selectMaterial(event.target.value);
  renderMaterial();
  $("#material-select").focus({ preventScroll: true });
});
$(".material-panel").addEventListener("keydown", (event) => {
  const tab = event.target.closest('[role="tab"]');
  if (!tab || !["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  const keys = materialTabs.map(([key]) => key);
  const index = keys.indexOf(currentTab);
  const next = {
    ArrowLeft: (index + keys.length - 1) % keys.length,
    ArrowRight: (index + 1) % keys.length,
    Home: 0,
    End: keys.length - 1,
  }[event.key];
  event.preventDefault();
  currentTab = keys[next];
  renderResult();
  $(`#tab-${currentTab}`).focus();
});
document.addEventListener("click", (event) => {
  const button = event.target.closest("[data-course]");
  if (button) openMaterial(button.dataset.course, button.dataset.material);
});
const notices = [
  {
    category: "학사",
    title: "2학기 중간 강의평가 안내",
    source: ["학사지원과", "11.06"],
    detail: "academic",
  },
  {
    category: "취업",
    title: "하반기 IT 기업 채용설명회",
    source: ["취업진로본부", "11.06"],
    detail: "career",
  },
  {
    category: "장학",
    title: "동계 국가근로장학금 신청 안내",
    source: ["장학복지팀", "11.05"],
    detail: "scholarship",
  },
  {
    category: "행사",
    title: "도서관과 함께하는 독서 챌린지",
    source: ["중앙도서관", "11.05"],
    detail: "event",
  },
];
function renderNotices(filter = "all") {
  const visible = notices.filter(
    (notice) => filter === "all" || notice.category === filter,
  );
  $("#notice-list").innerHTML = visible
    .map(
      (notice) =>
        `<button class="notice-item" data-detail="${notice.detail}"><span class="notice-category">${notice.category}</span><span class="notice-text"><strong>${notice.title}</strong><small class="meta"><span>${notice.source[0]}</span><span>${notice.source[1]}</span></small></span></button>`,
    )
    .join("");
  $(".count-label").textContent = `${visible.length}건`;
}
document.querySelectorAll("[data-filter]").forEach((button) =>
  button.addEventListener("click", () => {
    document.querySelectorAll("[data-filter]").forEach((tab) => {
      const selected = tab === button;
      tab.classList.toggle("selected", selected);
      tab.setAttribute("aria-pressed", String(selected));
    });
    renderNotices(button.dataset.filter);
  }),
);

const taskDialog = $("#task-dialog");
$("#add-task").addEventListener("click", () => {
  $("#task-form").reset();
  $("#task-form").elements.date.value = selectedDate;
  taskDialog.showModal();
  $("#task-form").elements.title.focus();
});
$("#close-task").addEventListener("click", () => taskDialog.close());
$("#task-form").elements.title.addEventListener("input", (event) =>
  event.target.setCustomValidity(""),
);
$("#task-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget,
    title = form.elements.title.value.trim();
  if (!title) {
    form.elements.title.setCustomValidity("일정 이름을 입력해 주세요.");
    form.elements.title.reportValidity();
    return;
  }
  const date = form.elements.date.value,
    time = form.elements.time.value;
  tasks.push({
    id: ++taskId,
    date,
    title,
    meta: ["개인 일정", time],
    badge: "개인 일정",
    kind: "personal",
    done: false,
  });
  selectedDate = date;
  weekStart = startOfWeek(date);
  refreshSchedule();
  taskDialog.close();
  showToast("개인 일정을 추가했어요. 새로고침하면 초기화됩니다.");
});
for (const dialog of [detailDialog, taskDialog])
  dialog.addEventListener("click", (event) => {
    if (event.target !== dialog) return;
    const rect = dialog.getBoundingClientRect();
    if (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    )
      dialog.close();
  });

function closePopovers() {
  for (const [button, panel] of [
    ["notification-button", "notifications"],
    ["profile-button", "profile-menu"],
  ]) {
    $(`#${panel}`).hidden = true;
    $(`#${button}`).setAttribute("aria-expanded", "false");
  }
}
for (const [button, panel] of [
  ["notification-button", "notifications"],
  ["profile-button", "profile-menu"],
]) {
  $(`#${button}`).addEventListener("click", () => {
    const opening = $(`#${panel}`).hidden;
    closePopovers();
    $(`#${panel}`).hidden = !opening;
    $(`#${button}`).setAttribute("aria-expanded", String(opening));
  });
}
document.addEventListener("click", (event) => {
  if (
    !event.target.closest(
      ".header-popover, .notification-button, .profile-button",
    )
  )
    closePopovers();
});

const recommendations = [
  ["취업", "IT 기업 채용설명회", ["11/12(목) 15:00", "다빈치관"], "career"],
  ["비교과", "백엔드 실무 프로젝트 과정", ["11/17(화) 개강", "8주 과정"], "project"],
  ["복지", "학생 소프트웨어 지원", ["JetBrains, GitHub Student 등"], "software"],
  ["복지", "2학기 기숙사 추가신청", ["11/10(화) 18:00 마감"], "dorm"],
];
$("#bot-recommendations").innerHTML = recommendations
  .map(
    ([category, title, meta, key]) =>
      `<article class="bot-rec"><h4>${title}</h4><p class="meta">${[category, ...meta].map((part) => `<span>${part}</span>`).join("")}</p><button data-detail="${key}">${key === "software" ? "혜택 살펴보기" : "모집 내용 보기"}</button></article>`,
  )
  .join("");
const botPanel = $("#bot-panel"),
  botFab = $("#bot-fab");
let botOpen = false,
  closeTimer;
function motionReduced() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}
function toggleBot(open, restoreFocus = true) {
  clearTimeout(closeTimer);
  botOpen = open;
  botFab.setAttribute("aria-expanded", String(open));
  updateFabLabel();
  botPanel.classList.remove("opening", "closing");
  if (open) {
    botPanel.hidden = false;
    botPanel.inert = false;
    botPanel.classList.add("opening");
    $("#close-bot").focus({ preventScroll: true });
  } else {
    botPanel.inert = true;
    if (restoreFocus) botFab.focus({ preventScroll: true });
    if (motionReduced()) botPanel.hidden = true;
    else {
      botPanel.classList.add("closing");
      closeTimer = setTimeout(() => {
        if (!botOpen) {
          botPanel.hidden = true;
          botPanel.classList.remove("closing");
        }
      }, 210);
    }
  }
}
function studyMode() {
  return !$(".material-panel").hidden;
}
function updateFabLabel() {
  botFab.setAttribute(
    "aria-label",
    botOpen ? "테크뇽 닫기" : studyMode() ? "테크뇽에게 질문하기" : "테크뇽 추천 열기",
  );
}
function renderBotMode() {
  const study = studyMode();
  $("#bot-recommend").hidden = study;
  $("#bot-study").hidden = !study;
  $("#bot-role").textContent = study ? "학습 도우미" : "학생 프로그램 추천";
  botPanel.setAttribute("aria-label", study ? "테크뇽 학습 도우미" : "테크뇽 프로그램 추천");
  $("#bot-footer-label").textContent = study ? "답변 근거" : "추천 기준";
  updateFabLabel();
  if (!study) {
    $("#bot-footer-value").textContent = "학과, 학년, 신청 기간";
    return;
  }
  const material = findMaterial();
  const ready = material.status === "done";
  $("#study-context-title").textContent = `${materialLabel(material)} | ${material.title}`;
  $("#study-intro").textContent = ready
    ? "이 자료의 요약과 원본을 근거로 답해요."
    : "요약이 끝나면 이 자료로 질문할 수 있어요.";
  $("#bot-footer-value").textContent = ready
    ? `${materialLabel(material)} | ${material.length}`
    : "요약이 끝난 뒤 표시돼요";
  $("#study-input").disabled = !ready;
  $("#study-ask button").disabled = !ready;
  renderStudy();
}
function renderStudy() {
  const material = findMaterial();
  const thread = $("#study-thread");
  thread.replaceChildren(
    ...studyThread.map(([who, text, cite]) => {
      const bubble = document.createElement("p");
      bubble.className = who === "q" ? "study-q" : "study-a";
      bubble.textContent = text;
      if (cite) {
        const source = document.createElement("small");
        source.textContent = `근거 ${cite}`;
        bubble.append(source);
      }
      return bubble;
    }),
  );
  const asked = new Set(studyThread.map(([, text]) => text));
  $("#study-suggestions").innerHTML = (material.questions || [])
    .map(([question], index) =>
      asked.has(question) ? "" : `<button data-question="${index}">${question}</button>`,
    )
    .join("");
}
function askStudy(question, answer, cite) {
  studyThread.push(["q", question], ["a", answer, cite]);
  renderStudy();
  botPanel.scrollTo({ top: botPanel.scrollHeight, behavior: motionReduced() ? "instant" : "smooth" });
}
$("#study-suggestions").addEventListener("click", (event) => {
  const button = event.target.closest("[data-question]");
  if (!button) return;
  const [question, answer, cite] = findMaterial().questions[Number(button.dataset.question)];
  askStudy(question, answer, cite);
  $("#study-input").focus({ preventScroll: true });
});
$("#study-ask").addEventListener("submit", (event) => {
  event.preventDefault();
  const input = $("#study-input");
  const question = input.value.trim();
  if (!question) return;
  const match = findMaterial().questions.find(([text]) => text === question);
  if (match) askStudy(...match);
  else askStudy(question, "목업에서는 위에 있는 예시 질문에만 답할 수 있어요.");
  input.value = "";
});
botFab.addEventListener("click", () => toggleBot(!botOpen));
$("#close-bot").addEventListener("click", () => toggleBot(false));
$("#open-recommendations").addEventListener("click", () => toggleBot(true));
document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape" || detailDialog.open || taskDialog.open) return;
  if (botOpen) {
    toggleBot(false);
    event.preventDefault();
  }
  const expanded = $('.header-actions [aria-expanded="true"]');
  if (expanded) {
    closePopovers();
    expanded.focus();
  }
});
let toastTimer;
function showToast(message) {
  clearTimeout(toastTimer);
  $("#toast").textContent = message;
  $("#toast").classList.add("visible");
  toastTimer = setTimeout(() => $("#toast").classList.remove("visible"), 3300);
}
refreshSchedule();
renderNotices();
