-- local 프로필 전용 예시 데이터. 실제 공고 아님.
INSERT INTO boards (school_id, board_name, created_at)
SELECT id, source_name, CURRENT_TIMESTAMP FROM school
CROSS JOIN (VALUES ('학사지원과'), ('취업진로본부'), ('장학복지팀'), ('중앙도서관')) AS sources(source_name)
WHERE name = '서울과학기술대학교';
INSERT INTO posts (school_id, board_id, source, category, title, url, excerpt, target_grade, published_at)
SELECT b.school_id, b.board_id, b.board_name, sample.category, sample.title, sample.url, '예시 요약 한 줄', NULL, sample.published_at
FROM boards b JOIN (
  VALUES ('학사지원과', 'ACADEMIC', '2학기 중간 강의평가 안내', 'https://example.invalid/seoultech/notice/1', TIMESTAMP WITH TIME ZONE '2026-11-06 09:00:00+09:00'),
    ('취업진로본부', 'CAREER', '하반기 IT 기업 채용설명회', 'https://example.invalid/seoultech/notice/2', TIMESTAMP WITH TIME ZONE '2026-11-06 10:00:00+09:00'),
    ('장학복지팀', 'SCHOLARSHIP', '동계 국가근로장학금 신청 안내', 'https://example.invalid/seoultech/notice/3', TIMESTAMP WITH TIME ZONE '2026-11-05 09:00:00+09:00'),
    ('중앙도서관', 'EVENT', '도서관과 함께하는 독서 챌린지', 'https://example.invalid/seoultech/notice/4', TIMESTAMP WITH TIME ZONE '2026-11-05 14:00:00+09:00')
) AS sample(source_name, category, title, url, published_at) ON b.board_name = sample.source_name
WHERE b.school_id = (SELECT id FROM school WHERE name = '서울과학기술대학교');
