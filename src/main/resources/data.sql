INSERT INTO todos (title, status, display_order)
SELECT seed.title, seed.status, seed.display_order
FROM (VALUES
    ('Spring Bootの基礎を復習する', 'NOT_STARTED', 1),
    ('MyBatisのMapperを作成する', 'COMPLETED', 2),
    ('ToDo一覧画面を確認する', 'NOT_STARTED', 3),
    ('テストコードを実行する', 'COMPLETED', 4),
    ('機能追加の内容をメモする', 'NOT_STARTED', 5)
) AS seed(title, status, display_order)
WHERE NOT EXISTS (SELECT 1 FROM todos)
ORDER BY seed.display_order;

INSERT INTO categories (name, color)
SELECT '事前準備', '#0D6EFD' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '事前準備');
INSERT INTO categories (name, color)
SELECT '申告書配布・回収', '#6F42C1' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '申告書配布・回収');
INSERT INTO categories (name, color)
SELECT '申告内容確認', '#FD7E14' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '申告内容確認');
INSERT INTO categories (name, color)
SELECT '年税額計算', '#198754' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '年税額計算');
INSERT INTO categories (name, color)
SELECT '書類提出・保管', '#795548' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '書類提出・保管');
INSERT INTO categories (name, color)
SELECT 'その他', '#6C757D' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'その他');

INSERT INTO assignees (name)
SELECT 'たろう' WHERE NOT EXISTS (SELECT 1 FROM assignees WHERE name = 'たろう');
INSERT INTO assignees (name)
SELECT 'はなこ' WHERE NOT EXISTS (SELECT 1 FROM assignees WHERE name = 'はなこ');
INSERT INTO assignees (name)
SELECT 'じろう' WHERE NOT EXISTS (SELECT 1 FROM assignees WHERE name = 'じろう');
INSERT INTO assignees (name)
SELECT 'さくら' WHERE NOT EXISTS (SELECT 1 FROM assignees WHERE name = 'さくら');
