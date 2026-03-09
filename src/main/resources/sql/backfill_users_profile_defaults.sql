-- Backfill legacy NULL user profile fields to match current non-null model expectations.
-- Safe to run multiple times.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'display_name'
    ) THEN
        UPDATE users
        SET display_name = COALESCE(NULLIF(user_name, ''), COALESCE(email, ''))
        WHERE display_name IS NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'avatar_url'
    ) THEN
        UPDATE users
        SET avatar_url = ''
        WHERE avatar_url IS NULL;
    END IF;
END $$;
