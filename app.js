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
  notices: "학교 소식",
  benefits: "복지·취업정보",
};
function setView(view) {
  if (!views[view]) return;
  document.querySelectorAll(".nav-button").forEach((button) => {
    const active = button.dataset.view === view;
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
  database: {
    category: "데이터베이스 PDF 핵심 요약",
    title: "관계형 데이터베이스 설계",
    body: '<p>정규화는 데이터 중복을 줄이고 삽입, 갱신, 삭제 이상을 방지하는 과정입니다.</p><ul><li>1NF: 각 속성은 원자값을 가집니다.</li><li>2NF: 비주요 속성의 부분 함수 종속을 제거합니다.</li><li>3NF: 비주요 속성의 이행적 종속을 제거합니다.</li><li>BCNF: 모든 비자명한 함수 종속의 결정자가 슈퍼키입니다.</li></ul><div class="detail-note">실제 PDF 분석 결과가 아닌, 학습 요약 화면을 위한 예시입니다.</div>',
  },
  os: {
    category: "운영체제 강의 영상 핵심 요약",
    title: "프로세스와 스레드",
    body: '<p>프로세스는 실행 중인 프로그램이며 독립적인 주소 공간을 가집니다. 같은 프로세스의 스레드는 메모리와 자원을 공유합니다.</p><ul><li>00:00 프로세스의 구성과 상태 전이</li><li>08:30 문맥 교환과 스케줄러</li><li>17:40 스레드의 구조와 자원 공유</li><li>25:10 동시성과 병렬성의 차이</li><li>33:00 경쟁 상태와 동기화</li><li>39:00 핵심 개념 정리</li></ul><div class="detail-note">예시 구간 안내입니다. 실제 강의 영상은 연결되지 않았습니다.</div>',
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
  botFab.setAttribute(
    "aria-label",
    open ? "테크뇽 추천 닫기" : "테크뇽 추천 열기",
  );
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
