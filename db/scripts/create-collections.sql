delete from collection_article;
delete from collection;

DO $$
DECLARE
    rec RECORD;
    new_collection_id integer;
BEGIN
    FOR rec IN
        SELECT k.id AS keyword_id, k.name AS keyword_name, COUNT(DISTINCT aks.article_id) AS article_count
        FROM keyword k
        JOIN article_keyword_score aks ON aks.keyword_id = k.id
        GROUP BY k.id, k.name
        HAVING COUNT(DISTINCT aks.article_id) > 10
        ORDER BY k.name
    LOOP
        -- Skip if a collection with this name already exists (idempotent)
        IF NOT EXISTS (SELECT 1 FROM collection WHERE name = rec.keyword_name AND user_id IS NULL) THEN

            INSERT INTO collection (user_id, name, description, is_public)
            VALUES (NULL, rec.keyword_name,
                    'Auto-generated collection for keyword "' || rec.keyword_name || '" (' || rec.article_count || ' articles)',
                    TRUE)
            RETURNING id INTO new_collection_id;

            INSERT INTO collection_article (collection_id, article_id)
            SELECT new_collection_id, sub.article_id
            FROM (
                SELECT DISTINCT aks.article_id
                FROM article_keyword_score aks
                WHERE aks.keyword_id = rec.keyword_id
            ) sub;

            RAISE NOTICE 'Created collection "%" (id=%) with % articles', rec.keyword_name, new_collection_id, rec.article_count;
        ELSE
            RAISE NOTICE 'Collection "%" already exists, skipping', rec.keyword_name;
        END IF;
    END LOOP;
END $$;


SELECT k.name, COUNT(DISTINCT aks.article_id) AS article_count
FROM keyword k
JOIN article_keyword_score aks ON aks.keyword_id = k.id
GROUP BY k.name
HAVING COUNT(DISTINCT aks.article_id) > 10
ORDER BY article_count DESC;
