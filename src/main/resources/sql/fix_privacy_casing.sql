-- ChefAI: normalize recipes.privacy to uppercase.
--
-- The original seed.sql wrote 'public'/'private' (lowercase) for its first 70 recipes and
-- 'PUBLIC' (uppercase) for the rest. Application code requires uppercase exactly:
-- PostgresSyncRepository/PostgresRecipesRepository filter on `privacy = 'PUBLIC'`, and
-- RecipeMapping.daoToModel does `enumValueOf(dao.privacy)`, which throws on any lowercase value.
-- seed.sql itself has been fixed going forward; run this once against any existing database
-- created before that fix (idempotent — safe to run more than once, and a no-op once everything
-- is already uppercase).

UPDATE recipes SET privacy = upper(privacy) WHERE privacy <> upper(privacy);
