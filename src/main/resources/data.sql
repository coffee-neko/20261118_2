INSERT INTO todos (title, completed) VALUES ('Spring Bootの基礎を復習する', FALSE);
INSERT INTO todos (title, completed) VALUES ('MyBatisのMapperを作成する', TRUE);
INSERT INTO todos (title, completed) VALUES ('ToDo一覧画面を確認する', FALSE);
INSERT INTO todos (title, completed) VALUES ('テストコードを実行する', TRUE);
INSERT INTO todos (title, completed) VALUES ('機能追加の内容をメモする', FALSE);
UPDATE todos
SET display_order = id
WHERE display_order = 0;
INSERT INTO categories (name, color) VALUES ('事前準備', '#0D6EFD');
INSERT INTO categories (name, color) VALUES ('申告書配布・回収', '#6F42C1');
INSERT INTO categories (name, color) VALUES ('申告内容確認', '#FD7E14');
INSERT INTO categories (name, color) VALUES ('年税額計算', '#198754');
INSERT INTO categories (name, color) VALUES ('書類提出・保管', '#795548');
INSERT INTO categories (name, color) VALUES ('その他', '#6C757D');
INSERT INTO assignees (name) VALUES ('たろう');
INSERT INTO assignees (name) VALUES ('はなこ');
INSERT INTO assignees (name) VALUES ('じろう');
INSERT INTO assignees (name) VALUES ('さくら');
