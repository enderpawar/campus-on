-- 연결 세대: 로그인(연결 생성·갱신)마다 새 값. 진행 중이던 이전 동기화가 끝나서 결과를 반영하려 할 때
-- 세대가 다르면(그 사이 로그아웃·재로그인) 버린다. 삭제 후 재생성도 구별해야 하므로 증가 카운터가 아닌 고유 값이다.
ALTER TABLE eclass_connections ADD COLUMN connection_generation VARCHAR(36);
UPDATE eclass_connections SET connection_generation = 'legacy-' || CAST(user_id AS VARCHAR(20));
ALTER TABLE eclass_connections ALTER COLUMN connection_generation SET NOT NULL;
