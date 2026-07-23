ALTER TABLE sina_finance_news
  ADD COLUMN category_tag INT NOT NULL DEFAULT 0 COMMENT '频道标签：0全部，10 A股' AFTER news_id,
  ADD COLUMN category_name VARCHAR(50) NOT NULL DEFAULT '全部' COMMENT '频道名称' AFTER category_tag,
  ADD KEY idx_sina_finance_news_category_time (category_tag, create_time);
