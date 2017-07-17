-- noinspection SqlNoDataSourceInspectionForFile
IF object_id('dbo.people') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[people]
END
CREATE TABLE people (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL, last_name VARCHAR(56), dob DATE, graduation_date DATE, created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.accounts') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[accounts]
END
CREATE TABLE accounts (id  INT IDENTITY PRIMARY KEY, account VARCHAR(56), description VARCHAR(56), amount DECIMAL(10,2), total DECIMAL(10,2));

IF object_id('dbo.temperatures') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[temperatures]
END
CREATE TABLE temperatures (id  INT IDENTITY PRIMARY KEY, temp SMALLINT);


IF object_id('dbo.shard1_temperatures') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_temperatures]
    END
CREATE TABLE shard1_temperatures (id  INT IDENTITY PRIMARY KEY, temp SMALLINT);


IF object_id('dbo.shard2_temperatures') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard2_temperatures]
    END
CREATE TABLE shard2_temperatures (id  INT IDENTITY PRIMARY KEY, temp SMALLINT);

IF object_id('dbo.salaries') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[salaries]
END
CREATE TABLE salaries (id  INT IDENTITY PRIMARY KEY, salary DECIMAL(7, 2));

IF object_id('dbo.users') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[users]
END
CREATE TABLE users (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56));

IF object_id('dbo.shard1_users') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_users]
    END
CREATE TABLE shard1_users (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56));

IF object_id('dbo.addresses') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[addresses]
END
CREATE TABLE addresses (id  INT IDENTITY PRIMARY KEY, address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56), user_id INT);

IF object_id('dbo.shard1_addresses') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_addresses]
    END
CREATE TABLE shard1_addresses (id  INT IDENTITY PRIMARY KEY, address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56), user_id INT);


IF object_id('dbo.rooms') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[rooms]
END
CREATE TABLE rooms (id  INT IDENTITY PRIMARY KEY, name VARCHAR(56), address_id INT);

IF object_id('dbo.legacy_universities') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[legacy_universities]
END
CREATE TABLE legacy_universities (id  INT IDENTITY PRIMARY KEY, univ_name VARCHAR(56), address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56));

IF object_id('dbo.libraries') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[libraries]
END
CREATE TABLE libraries (id  INT IDENTITY PRIMARY KEY, address VARCHAR(56), city VARCHAR(56), state VARCHAR(56));

IF object_id('dbo.books') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[books]
END
CREATE TABLE books (id  INT IDENTITY PRIMARY KEY, title VARCHAR(56), author VARCHAR(56), isbn VARCHAR(56), lib_id INT);

IF object_id('dbo.readers') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[readers]
END
CREATE TABLE readers (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), book_id INT);

IF object_id('dbo.animals') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[animals]
END
CREATE TABLE animals (animal_id  INT IDENTITY PRIMARY KEY, animal_name VARCHAR(56));

IF object_id('dbo.patients') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[patients]
END
CREATE TABLE patients (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56));

IF object_id('dbo.shard1_patients') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_patients]
    END
CREATE TABLE shard1_patients (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56));

IF object_id('dbo.prescriptions') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[prescriptions]
END
CREATE TABLE prescriptions (id  INT IDENTITY PRIMARY KEY, name VARCHAR(56), patient_id INT);

IF object_id('dbo.doctors') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[doctors]
END
CREATE TABLE doctors (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), discipline varchar(56));

IF object_id('dbo.shard1_doctors') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_doctors]
    END
CREATE TABLE shard1_doctors (id  INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), discipline varchar(56));


IF object_id('dbo.doctors_patients') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[doctors_patients]
END
CREATE TABLE doctors_patients (id  INT IDENTITY PRIMARY KEY, doctor_id INT, patient_id INT);

IF object_id('dbo.students') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[students]
END
CREATE TABLE students (id INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), dob DATE, enrollment_date DATETIME2);

IF object_id('dbo.courses') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[courses]
END
CREATE TABLE courses (id  INT IDENTITY PRIMARY KEY, course_name VARCHAR(56));

IF object_id('dbo.registrations') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[registrations]
END
CREATE TABLE registrations (id  INT IDENTITY PRIMARY KEY, astudent_id INT, acourse_id INT);


IF object_id('dbo.items') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[items]
END
CREATE TABLE items (id  INT IDENTITY PRIMARY KEY, item_number INT, item_description VARCHAR(56), lock_version INT);

IF object_id('dbo.articles') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[articles]
END
CREATE TABLE articles (id  INT IDENTITY PRIMARY KEY, title VARCHAR(56), content TEXT);

IF object_id('dbo.shard1_articles') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_articles]
    END
CREATE TABLE shard1_articles (id  INT IDENTITY PRIMARY KEY, title VARCHAR(56), content TEXT);

IF object_id('dbo.posts') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[posts]
END
CREATE TABLE posts (id  INT IDENTITY PRIMARY KEY, title VARCHAR(56), post TEXT);

IF object_id('dbo.shard1_posts') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_posts]
    END
CREATE TABLE shard1_posts (id  INT IDENTITY PRIMARY KEY, title VARCHAR(56), post TEXT);

IF object_id('dbo.comments') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[comments]
END
CREATE TABLE comments (id  INT IDENTITY PRIMARY KEY, author VARCHAR(56), content TEXT, parent_id INT, parent_type VARCHAR(256));

IF object_id('dbo.shard1_comments') IS NOT NULL
    BEGIN
        DROP TABLE [dbo].[shard1_comments]
    END
CREATE TABLE shard1_comments (id  INT IDENTITY PRIMARY KEY, author VARCHAR(56), content TEXT, parent_id INT, parent_type VARCHAR(256));

IF object_id('dbo.tags') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[tags]
END
CREATE TABLE tags (id  INT IDENTITY PRIMARY KEY, content TEXT, parent_id INT, parent_type VARCHAR(256));


IF object_id('dbo.fruits') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[fruits]
END
CREATE TABLE fruits (id  INT IDENTITY PRIMARY KEY, fruit_name VARCHAR(56), category VARCHAR(56), created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.vegetables') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[vegetables]
END
CREATE TABLE vegetables (id  INT IDENTITY PRIMARY KEY, vegetable_name VARCHAR(56), category VARCHAR(56), created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.plants') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[plants]
END
CREATE TABLE plants (id  INT IDENTITY PRIMARY KEY, plant_name VARCHAR(56), category VARCHAR(56), created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.pages') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[pages]
END
CREATE TABLE pages ( id INT IDENTITY PRIMARY KEY, description VARCHAR(56), word_count INT, [order] INT);


IF object_id('dbo.watermelons') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[watermelons]
END
CREATE TABLE watermelons ( id INT IDENTITY PRIMARY KEY, melon_type VARCHAR(56), record_version INT, created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.schools') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[schools]
END
CREATE TABLE schools ( id INT IDENTITY PRIMARY KEY, school_name VARCHAR(56), school_type VARCHAR(56), email VARCHAR(56), created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.dual') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[dual]
END
CREATE TABLE dual ( id INT IDENTITY PRIMARY KEY, next_val BIGINT);
INSERT INTO dual (next_val) VALUES (0);

IF object_id('dbo.programmers') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[programmers]
END
CREATE TABLE programmers ( id INT IDENTITY PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56), created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.projects') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[projects]
END
CREATE TABLE projects ( id INT IDENTITY PRIMARY KEY, project_name VARCHAR(56), created_at DATETIME2, updated_at DATETIME2);

IF object_id('dbo.programmers_projects') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[programmers_projects]
END
CREATE TABLE programmers_projects ( id INT IDENTITY PRIMARY KEY, duration_weeks INT, project_id INT, programmer_id INT, created_at DATETIME2, updated_at DATETIME2);


IF object_id('dbo.computers') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[computers]
END
CREATE TABLE computers ( id INT IDENTITY PRIMARY KEY, description VARCHAR(56), mother_id INT, key_id INT);

IF object_id('dbo.keyboards') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[keyboards]
END
CREATE TABLE keyboards ( id INT IDENTITY PRIMARY KEY, description VARCHAR(56));

IF object_id('dbo.motherboards') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[motherboards]
END
CREATE TABLE motherboards ( id INT IDENTITY PRIMARY KEY, description VARCHAR(56));



IF object_id('dbo.ingredients') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[ingredients]
END
CREATE TABLE ingredients (ingredient_id  INT IDENTITY PRIMARY KEY, ingredient_name VARCHAR(56));

IF object_id('dbo.recipes') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[recipes]
END
CREATE TABLE recipes (recipe_id  INT IDENTITY PRIMARY KEY, recipe_name VARCHAR(56));

IF object_id('dbo.ingredients_recipes') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[ingredients_recipes]
END
CREATE TABLE ingredients_recipes (the_id  INT IDENTITY PRIMARY KEY, recipe_id INT, ingredient_id INT);


IF object_id('dbo.vehicles') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[vehicles]
END
CREATE TABLE vehicles (id  INT IDENTITY PRIMARY KEY, name VARCHAR(56));

IF object_id('dbo.mammals') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[mammals]
END
CREATE TABLE mammals (id  INT IDENTITY PRIMARY KEY, name VARCHAR(56));

IF object_id('dbo.classifications') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[classifications]
END
CREATE TABLE classifications (id  INT IDENTITY PRIMARY KEY, name VARCHAR(56), parent_id INT, parent_type VARCHAR(56));

IF object_id('dbo.sub_classifications') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[sub_classifications]
END
CREATE TABLE sub_classifications (id  INT IDENTITY PRIMARY KEY, name VARCHAR(56), classification_id INT);

IF object_id('dbo.content_groups') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[content_groups]
END
create table content_groups ( id  INT IDENTITY PRIMARY KEY, group_name INT);

IF object_id('dbo.cakes') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[cakes]
END
CREATE TABLE cakes (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL);

IF object_id('dbo.swords') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[swords]
END
CREATE TABLE swords (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL);

IF object_id('dbo.meals') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[meals]
END
CREATE TABLE meals (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL);

IF object_id('dbo.Member') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[Member]
END
CREATE TABLE Member (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL);


IF object_id('dbo.nodes') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[nodes]
END
CREATE TABLE nodes (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL, parent_id INT);


IF object_id('dbo.images') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[images]
END
CREATE TABLE images (id INT IDENTITY PRIMARY KEY, name VARCHAR(56) NOT NULL, content VARBINARY(MAX));


IF object_id('dbo.apples') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[apples]
END
CREATE TABLE apples (id INT PRIMARY KEY, apple_type VARCHAR(56) NOT NULL);


IF object_id('dbo.alarms') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[alarms]
END
CREATE TABLE alarms (id INT IDENTITY PRIMARY KEY, alarm_time TIME NOT NULL);


IF object_id('dbo.developers') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[developers]
END
CREATE TABLE developers (first_name VARCHAR(56) NOT NULL, last_name VARCHAR(56) NOT NULL, email VARCHAR(56) NOT NULL, address VARCHAR(56), CONSTRAINT developers_uq UNIQUE (first_name, last_name, email));


IF object_id('dbo.boxes') IS NOT NULL
BEGIN
    DROP TABLE [dbo].[boxes]
END
CREATE TABLE boxes (id INT IDENTITY PRIMARY KEY, color VARCHAR(56) NOT NULL, fruit_id INT);