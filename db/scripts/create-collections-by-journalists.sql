-- Rebuild only auto-generated public journalist collections.
-- This avoids touching private/user collections.
DELETE FROM collection_article ca
USING collection c
WHERE ca.collection_id = c.id
  AND c.user_id IS NULL
  AND c.is_public = TRUE
  AND c.description LIKE 'Auto-generated journalist collection for author "%" (% articles)';

DELETE FROM collection c
WHERE c.user_id IS NULL
  AND c.is_public = TRUE
  AND c.description LIKE 'Auto-generated journalist collection for author "%" (% articles)';

DO $$
DECLARE
    rec RECORD;
    new_collection_id integer;
BEGIN
    FOR rec IN
        SELECT a.id AS author_id,
               a.name AS author_name,
               COUNT(DISTINCT ar.id) AS article_count
        FROM author a
        JOIN article ar ON ar.author_id = a.id
        WHERE a.name IS NOT NULL
          AND btrim(a.name) <> ''
        GROUP BY a.id, a.name
        ORDER BY a.name
    LOOP
        INSERT INTO collection (user_id, name, description, is_public)
        VALUES (
            NULL,
            rec.author_name,
            'Auto-generated journalist collection for author "' || rec.author_name || '" (' || rec.article_count || ' articles)',
            TRUE
        )
        RETURNING id INTO new_collection_id;

        INSERT INTO collection_article (collection_id, article_id)
        SELECT new_collection_id, ar.id
        FROM article ar
        WHERE ar.author_id = rec.author_id;

        RAISE NOTICE 'Created collection "%" (id=%) with % articles', rec.author_name, new_collection_id, rec.article_count;
    END LOOP;
END $$;


SELECT a.name AS author_name,
       COUNT(DISTINCT ar.id) AS article_count
FROM author a
JOIN article ar ON ar.author_id = a.id
GROUP BY a.name
ORDER BY article_count DESC, author_name;

