-- This script fixes the "Function Search Path Mutable" lint issue.
-- It sets a secure search_path for the count_estimate function.

-- If the function is in a standard schema (e.g., public):
-- ALTER FUNCTION public.count_estimate(text) SET search_path = pg_catalog, public;

-- For the specific temporary schema mentioned in the lint (pg_temp_27):
-- We use a dynamic SQL block to handle the temp schema safely.

DO $$
BEGIN
    -- Check if a function named count_estimate exists in any temporary schema
    IF EXISTS (
        SELECT 1 
        FROM pg_proc p 
        JOIN pg_namespace n ON p.pronamespace = n.oid 
        WHERE n.nspname LIKE 'pg_temp_%' 
          AND p.proname = 'count_estimate'
    ) THEN
        -- Execute the ALTER statement for the first matching temporary function found
        EXECUTE 'ALTER FUNCTION ' || (
            SELECT n.nspname || '.' || p.proname 
            FROM pg_proc p 
            JOIN pg_namespace n ON p.pronamespace = n.oid 
            WHERE n.nspname LIKE 'pg_temp_%' 
              AND p.proname = 'count_estimate' 
            LIMIT 1
        ) || '(text) SET search_path = pg_catalog, public;';
        
        RAISE NOTICE 'Successfully updated search_path for temporary count_estimate function.';
    ELSE
        RAISE NOTICE 'No count_estimate function found in temporary schemas.';
    END IF;
END $$;
