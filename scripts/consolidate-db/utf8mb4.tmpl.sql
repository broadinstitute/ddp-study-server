-- This script helps migrate database to use the utf8mb4 character set and the
-- utf8mb4_unicode_ci collation. utf8mb4 because it has full Unicode support,
-- such as for emojis. utf8mb4_unicode_ci because it works with utf8mb4 and
-- seems to give better sorts.
--
-- At a high-level, this script will convert the given database schema, all of
-- its tables, and all of their text-like columns to use new ut8mb4 encoding.
--
-- Typically, you will also want to run repair/optimize on the tables. But
-- since the data will be merged into a centralized database, we will not worry
-- about that here.

-- Stored procedures lives in a schema, so use the one we're migrating.
USE <<DB_NAME>>;

-- First, define the stored procedures to handle utf8mb4 migration.

DELIMITER //

DROP PROCEDURE IF EXISTS migrate_tables_to_utf8mb4 //
CREATE PROCEDURE migrate_tables_to_utf8mb4()
    BEGIN
    DECLARE curr_table_name varchar(255);

    DECLARE done int DEFAULT false;
    DECLARE tables_iter CURSOR FOR
        SELECT table_name
          FROM information_schema.tables
         WHERE table_schema = '<<DB_NAME>>';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = true;

    OPEN tables_iter;
    read_loop: LOOP
        FETCH FROM tables_iter INTO curr_table_name;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET @sql = CONCAT('ALTER TABLE <<DB_NAME>>.', curr_table_name,
            ' CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DROP PREPARE stmt;

        SELECT CONCAT('Converted table: ', curr_table_name) AS '';
    END LOOP;
    CLOSE tables_iter;
END //

DROP PROCEDURE IF EXISTS migrate_columns_to_utf8mb4 //
CREATE PROCEDURE migrate_columns_to_utf8mb4()
    BEGIN
    DECLARE curr_table_name, col_name, col_type varchar(255);

    DECLARE done int DEFAULT false;
    DECLARE columns_iter CURSOR FOR
        SELECT table_name, column_name, column_type
          FROM information_schema.columns
         WHERE table_schema = '<<DB_NAME>>'
           AND (column_type LIKE 'varchar%' OR column_type LIKE '%text%');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = true;

    OPEN columns_iter;
    read_loop: LOOP
        FETCH FROM columns_iter INTO curr_table_name, col_name, col_type;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET @sql = CONCAT('ALTER TABLE <<DB_NAME>>.', curr_table_name, ' MODIFY ', col_name, ' ', col_type,
            ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DROP PREPARE stmt;

        SELECT CONCAT('Converted column: ', curr_table_name, '.', col_name, ' ', col_type) AS '';
    END LOOP;
    CLOSE columns_iter;
END //

DELIMITER ;

-- Now, do the migration.

ALTER DATABASE <<DB_NAME>> CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
CALL migrate_tables_to_utf8mb4();
SELECT '-----------------------------------------------' AS '';
CALL migrate_columns_to_utf8mb4();

-- Cleanup stored procedues.

DROP PROCEDURE migrate_tables_to_utf8mb4;
DROP PROCEDURE migrate_columns_to_utf8mb4;
