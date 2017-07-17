-- noinspection SqlNoDataSourceInspectionForFile

CREATE TABLE people (id  NUMBER NOT NULL, name VARCHAR(56) NOT NULL, last_name VARCHAR(56), dob DATE, graduation_date DATE, created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE people ADD CONSTRAINT people_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE people_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER people_trigger
    BEFORE INSERT ON people REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, people_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE accounts (id  NUMBER NOT NULL, account VARCHAR(56), description VARCHAR(56), amount NUMBER(10,2), total NUMBER(10,2))
-- BREAK
ALTER TABLE accounts ADD CONSTRAINT accounts_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE accounts_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER accounts_trigger
    BEFORE INSERT ON accounts REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, accounts_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE temperatures (id  NUMBER NOT NULL, temp NUMBER)
-- BREAK
ALTER TABLE temperatures ADD CONSTRAINT temperatures_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE temperatures_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER temperatures_trigger
    BEFORE INSERT ON temperatures REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, temperatures_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE shard1_temperatures (id  NUMBER NOT NULL, temp NUMBER)
-- BREAK
ALTER TABLE shard1_temperatures ADD CONSTRAINT shard1_temperatures_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_temperatures_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_temperatures_trigger
BEFORE INSERT ON shard1_temperatures REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_temperatures_seq.nextval) into :new.id from dual;
    end;


-- BREAK
CREATE TABLE shard2_temperatures (id  NUMBER NOT NULL, temp NUMBER)
-- BREAK
ALTER TABLE shard2_temperatures ADD CONSTRAINT shard2_temperatures_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard2_temperatures_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard2_temperatures_trigger
BEFORE INSERT ON shard2_temperatures REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard2_temperatures_seq.nextval) into :new.id from dual;
    end;


-- BREAK
CREATE TABLE salaries (id  NUMBER NOT NULL, salary NUMBER(7, 2))
-- BREAK
ALTER TABLE salaries ADD CONSTRAINT salaries_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE salaries_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER salaries_trigger
    BEFORE INSERT ON salaries REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, salaries_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE users (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56))
-- BREAK
ALTER TABLE users ADD CONSTRAINT users_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER users_trigger
    BEFORE INSERT ON users REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, users_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE shard1_users (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56))
-- BREAK
ALTER TABLE shard1_users ADD CONSTRAINT shard1_users_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_users_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_users_trigger
BEFORE INSERT ON shard1_users REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_users_seq.nextval) into :new.id from dual;
    end;




-- BREAK
CREATE TABLE addresses (id  NUMBER NOT NULL, address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56), user_id NUMBER)
-- BREAK
ALTER TABLE addresses ADD CONSTRAINT addresses_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE addresses_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER addresses_trigger
    BEFORE INSERT ON addresses REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, addresses_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE shard1_addresses (id  NUMBER NOT NULL, address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56), user_id NUMBER)
-- BREAK
ALTER TABLE shard1_addresses ADD CONSTRAINT shard1_addresses_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_addresses_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_addresses_trigger
BEFORE INSERT ON shard1_addresses REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_addresses_seq.nextval) into :new.id from dual;
    end;


-- BREAK
CREATE TABLE rooms (id  NUMBER NOT NULL, name VARCHAR(56), address_id NUMBER)
-- BREAK
ALTER TABLE rooms ADD CONSTRAINT rooms_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE rooms_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER rooms_trigger
    BEFORE INSERT ON rooms REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, rooms_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE legacy_universities (id  NUMBER NOT NULL, univ_name VARCHAR(56), address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56))
-- BREAK
ALTER TABLE legacy_universities ADD CONSTRAINT legacy_universities_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE legacy_universities_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER legacy_universities_trigger
    BEFORE INSERT ON legacy_universities REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, legacy_universities_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE libraries (id  NUMBER NOT NULL, address VARCHAR(56), city VARCHAR(56), state VARCHAR(56))
-- BREAK
ALTER TABLE libraries ADD CONSTRAINT libraries_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE libraries_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER libraries_trigger
    BEFORE INSERT ON libraries REFERENCING
    NEW AS new
    FOR EACH ROW
    begin
select coalesce(:new.id, libraries_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE books (id  NUMBER NOT NULL, title VARCHAR(56), author VARCHAR(56), isbn VARCHAR(56), lib_id NUMBER(11))
-- BREAK
ALTER TABLE books ADD CONSTRAINT books_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE books_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER books_trigger
    BEFORE INSERT ON libraries REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, books_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE readers (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), book_id NUMBER(11))
-- BREAK
ALTER TABLE readers ADD CONSTRAINT readers_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE readers_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER readers_trigger
    BEFORE INSERT ON readers REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, readers_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE animals (animal_id  NUMBER NOT NULL, animal_name VARCHAR(56))
-- BREAK
ALTER TABLE animals ADD CONSTRAINT animals_pk PRIMARY KEY ( animal_id )
-- BREAK
CREATE SEQUENCE animals_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER animals_trigger
    BEFORE INSERT ON animals REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.animal_id, animals_seq.nextval) into :new.animal_id from dual;
end;




-- BREAK
CREATE TABLE patients (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56))
-- BREAK
ALTER TABLE patients ADD CONSTRAINT patients_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE patients_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER patients_trigger
    BEFORE INSERT ON patients REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, patients_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE shard1_patients (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56))
-- BREAK
ALTER TABLE shard1_patients ADD CONSTRAINT shard1_patients_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_patients_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_patients_trigger
BEFORE INSERT ON shard1_patients REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_patients_seq.nextval) into :new.id from dual;
    end;


-- BREAK
CREATE TABLE prescriptions (id  NUMBER NOT NULL, name VARCHAR(56), patient_id NUMBER)
-- BREAK
ALTER TABLE prescriptions ADD CONSTRAINT prescriptions_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE prescriptions_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER prescriptions_trigger
    BEFORE INSERT ON prescriptions REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, prescriptions_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE doctors (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), discipline varchar(56))
-- BREAK
ALTER TABLE doctors ADD CONSTRAINT doctors_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE doctors_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER doctors_trigger
    BEFORE INSERT ON doctors REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, doctors_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE shard1_doctors (id  NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), discipline varchar(56))
-- BREAK
ALTER TABLE shard1_doctors ADD CONSTRAINT shard1_doctors_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_doctors_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_doctors_trigger
BEFORE INSERT ON shard1_doctors REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_doctors_seq.nextval) into :new.id from dual;
    end;


-- BREAK
CREATE TABLE doctors_patients (id  NUMBER NOT NULL, doctor_id NUMBER(11), patient_id NUMBER(11))
-- BREAK
ALTER TABLE doctors_patients ADD CONSTRAINT doctors_patients_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE doctors_patients_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER doctors_patients_trigger
    BEFORE INSERT ON doctors_patients REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, doctors_patients_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE students (id NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), dob DATE, enrollment_date TIMESTAMP)
-- BREAK
ALTER TABLE students ADD CONSTRAINT students_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE students_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER students_trigger
    BEFORE INSERT ON students REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, students_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE courses (id  NUMBER NOT NULL, course_name VARCHAR(56))
-- BREAK
ALTER TABLE courses ADD CONSTRAINT courses_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE courses_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER courses_trigger
    BEFORE INSERT ON courses REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, courses_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE registrations (id  NUMBER NOT NULL, astudent_id NUMBER(11), acourse_id NUMBER(11))
-- BREAK
ALTER TABLE registrations ADD CONSTRAINT registrations_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE registrations_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER registrations_trigger
    BEFORE INSERT ON registrations REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, registrations_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE items (id  NUMBER NOT NULL , item_number NUMBER(11), item_description VARCHAR(56), lock_version NUMBER(11))
-- BREAK
ALTER TABLE items ADD CONSTRAINT items_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE items_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER items_trigger
    BEFORE INSERT ON items REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, items_seq.nextval) into :new.id from dual;
end;






-- BREAK
CREATE TABLE articles (id  NUMBER NOT NULL, title VARCHAR(56), content CLOB)
-- BREAK
ALTER TABLE articles ADD CONSTRAINT articles_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE articles_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER articles_trigger
    BEFORE INSERT ON articles REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, articles_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE shard1_articles (id  NUMBER NOT NULL, title VARCHAR(56), content CLOB)
-- BREAK
ALTER TABLE shard1_articles ADD CONSTRAINT shard1_articles_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_articles_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_articles_trigger
BEFORE INSERT ON shard1_articles REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_articles_seq.nextval) into :new.id from dual;
    end;

-- BREAK
CREATE TABLE posts (id  NUMBER NOT NULL, title VARCHAR(56), post VARCHAR(1024))
-- BREAK
ALTER TABLE posts ADD CONSTRAINT posts_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE posts_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER posts_trigger
    BEFORE INSERT ON posts REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, posts_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE shard1_posts (id  NUMBER NOT NULL, title VARCHAR(56), post VARCHAR(1024))
-- BREAK
ALTER TABLE shard1_posts ADD CONSTRAINT shard1_posts_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_posts_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_posts_trigger
BEFORE INSERT ON shard1_posts REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_posts_seq.nextval) into :new.id from dual;
    end;

-- BREAK
CREATE TABLE comments (id  NUMBER NOT NULL, author VARCHAR(56), content VARCHAR(128), parent_id NUMBER(11), parent_type VARCHAR(256))
-- BREAK
ALTER TABLE comments ADD CONSTRAINT comments_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE comments_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER comments_trigger
    BEFORE INSERT ON comments REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, comments_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE shard1_comments (id  NUMBER NOT NULL, author VARCHAR(56), content VARCHAR(128), parent_id NUMBER(11), parent_type VARCHAR(256))
-- BREAK
ALTER TABLE shard1_comments ADD CONSTRAINT shard1_comments_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE shard1_comments_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER shard1_comments_trigger
BEFORE INSERT ON shard1_comments REFERENCING
    NEW AS new
    OLD AS old
FOR EACH ROW
    begin
        select coalesce(:new.id, shard1_comments_seq.nextval) into :new.id from dual;
    end;

-- BREAK
CREATE TABLE tags (id  NUMBER NOT NULL, content VARCHAR(128), parent_id NUMBER(11), parent_type VARCHAR(256))
-- BREAK
ALTER TABLE tags ADD CONSTRAINT tags_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE tags_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER tags_trigger
    BEFORE INSERT ON comments REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, tags_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE fruits (id  NUMBER NOT NULL, fruit_name VARCHAR(56), category VARCHAR(56), created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE fruits ADD CONSTRAINT fruits_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE fruits_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER fruits_trigger
    BEFORE INSERT ON fruits REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, fruits_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE vegetables (id  NUMBER NOT NULL, vegetable_name VARCHAR(56), category VARCHAR(56), created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE vegetables ADD CONSTRAINT vegetables_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE vegetables_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER vegetables_trigger
    BEFORE INSERT ON vegetables REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, vegetables_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE plants (id  NUMBER NOT NULL, plant_name VARCHAR(56), category VARCHAR(56), created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE plants ADD CONSTRAINT plants_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE plants_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER plants_trigger
    BEFORE INSERT ON plants REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, plants_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE pages ( id NUMBER NOT NULL, description VARCHAR(56), word_count NUMBER, "order" NUMBER)
-- BREAK
ALTER TABLE pages ADD CONSTRAINT pages_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE pages_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER pages_trigger
    BEFORE INSERT ON pages REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, pages_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE watermelons ( id NUMBER NOT NULL, melon_type VARCHAR(56), record_version NUMBER, created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE watermelons ADD CONSTRAINT watermelons_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE watermelons_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER watermelons_trigger
    BEFORE INSERT ON watermelons REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, watermelons_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE schools ( id NUMBER NOT NULL, school_name VARCHAR(56), school_type VARCHAR(56), email VARCHAR(56), created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE schools ADD CONSTRAINT schools_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE schools_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER schools_trigger
    BEFORE INSERT ON schools REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, schools_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE programmers ( id NUMBER NOT NULL, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56), created_at TIMESTAMP, updated_at TIMESTAMP)
-- BREAK
ALTER TABLE programmers ADD CONSTRAINT programmers_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE programmers_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER programmers_trigger
    BEFORE INSERT ON programmers REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, programmers_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE projects ( id NUMBER NOT NULL, project_name VARCHAR(56), created_at TIMESTAMP, updated_at TIMESTAMP) 
-- BREAK
ALTER TABLE projects ADD CONSTRAINT projects_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE projects_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER projects_trigger
    BEFORE INSERT ON projects REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, projects_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE programmers_projects ( id NUMBER NOT NULL, duration_weeks NUMBER(3), project_id NUMBER, programmer_id NUMBER, created_at TIMESTAMP, updated_at TIMESTAMP) 
-- BREAK
ALTER TABLE programmers_projects ADD CONSTRAINT programmers_projects_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE programmers_projects_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER programmers_projects_trigger
    BEFORE INSERT ON programmers_projects REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, programmers_projects_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE keyboards ( id NUMBER NOT NULL, description VARCHAR(56))
-- BREAK
ALTER TABLE keyboards ADD CONSTRAINT keyboards_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE keyboards_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER keyboards_trigger
    BEFORE INSERT ON keyboards REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, keyboards_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE motherboards ( id NUMBER NOT NULL, description VARCHAR(56))
-- BREAK
ALTER TABLE motherboards ADD CONSTRAINT motherboards_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE motherboards_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER motherboards_trigger
    BEFORE INSERT ON motherboards REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, motherboards_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE computers (
  id NUMBER NOT NULL,
  description VARCHAR(56),
  mother_id NUMBER(11),
  key_id NUMBER(11))
-- BREAK
ALTER TABLE computers ADD CONSTRAINT computers_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE computers_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER computers_trigger
    BEFORE INSERT ON computers REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, computers_seq.nextval) into :new.id from dual;
end;




-- BREAK
CREATE TABLE ingredients (
  ingredient_id NUMBER NOT NULL,
  ingredient_name VARCHAR(56))
-- BREAK
ALTER TABLE ingredients ADD CONSTRAINT ingredients_pk PRIMARY KEY ( ingredient_id )
-- BREAK
CREATE SEQUENCE ingredients_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER ingredients1_trigger
    BEFORE INSERT ON ingredients REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.ingredient_id, ingredients_seq.nextval) into :new.ingredient_id from dual;
end;

-- BREAK
CREATE TABLE recipes (
  recipe_id NUMBER NOT NULL,
  recipe_name VARCHAR(56))
-- BREAK
ALTER TABLE recipes ADD CONSTRAINT recipes_pk PRIMARY KEY ( recipe_id )
-- BREAK
CREATE SEQUENCE recipes_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER recipes_trigger
    BEFORE INSERT ON recipes REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.recipe_id, recipes_seq.nextval) into :new.recipe_id from dual;
end;


-- BREAK
CREATE TABLE ingredients_recipes (
  the_id NUMBER NOT NULL,
  recipe_id NUMBER,
  ingredient_id NUMBER)
-- BREAK
ALTER TABLE ingredients_recipes ADD CONSTRAINT ingredients_recipes_pk PRIMARY KEY ( the_id )
-- BREAK
CREATE SEQUENCE ingredients_recipes_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER ingredients_recipes_trigger
    BEFORE INSERT ON ingredients_recipes REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.the_id, ingredients_recipes_seq.nextval) into :new.the_id from dual;
end;



-- BREAK
CREATE TABLE vehicles (id  NUMBER NOT NULL, name VARCHAR(56))
-- BREAK
ALTER TABLE vehicles ADD CONSTRAINT vehicles_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE vehicles_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER vehicles_trigger
    BEFORE INSERT ON vehicles REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, vehicles_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE mammals (id  NUMBER NOT NULL, name VARCHAR(56))
-- BREAK
ALTER TABLE mammals ADD CONSTRAINT mammals_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE mammals_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER mammals_trigger
    BEFORE INSERT ON mammals REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, mammals_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE classifications (id  NUMBER NOT NULL, name VARCHAR(56), parent_id NUMBER, parent_type VARCHAR(56))
-- BREAK
ALTER TABLE classifications ADD CONSTRAINT classifications_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE classifications_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER classifications_trigger
    BEFORE INSERT ON classifications REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, classifications_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE sub_classifications (id  NUMBER NOT NULL, name VARCHAR(56), classification_id NUMBER)
-- BREAK
ALTER TABLE sub_classifications ADD CONSTRAINT sub_classifications_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE sub_classifications_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER sub_classifications_trigger
    BEFORE INSERT ON sub_classifications REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, sub_classifications_seq.nextval) into :new.id from dual;
end;


-- BREAK
create table content_groups ( id  NUMBER NOT NULL, group_name NUMBER )
-- BREAK
ALTER TABLE content_groups ADD CONSTRAINT content_groups_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE content_groups_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER content_groups_trigger
    BEFORE INSERT ON content_groups REFERENCING
    NEW AS new
    OLD AS old
    FOR EACH ROW
    begin
select coalesce(:new.id, content_groups_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE cakes (id NUMBER NOT NULL, name VARCHAR(56) NOT NULL)
-- BREAK
ALTER TABLE cakes ADD CONSTRAINT cakes_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE cakes_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER cakes_trigger
	BEFORE INSERT ON cakes REFERENCING
	NEW AS new
	OLD AS old
	FOR EACH ROW
	begin
select coalesce(:new.id, cakes_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE swords (id NUMBER NOT NULL, name VARCHAR(56) NOT NULL)
-- BREAK
ALTER TABLE swords ADD CONSTRAINT swords_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE swords_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER swords_trigger
        BEFORE INSERT ON swords REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, swords_seq.nextval) into :new.id from dual;
end;

-- BREAK
CREATE TABLE meals (id NUMBER NOT NULL, name VARCHAR(56) NOT NULL)
-- BREAK
ALTER TABLE meals ADD CONSTRAINT meals_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE meals_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER meals_trigger
        BEFORE INSERT ON meals REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, meals_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE Member (id NUMBER NOT NULL, name VARCHAR(56) NOT NULL)
-- BREAK
ALTER TABLE Member ADD CONSTRAINT Member_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE Member_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER Member_trigger
        BEFORE INSERT ON Member REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, Member_seq.nextval) into :new.id from dual;
end;



-- BREAK
CREATE TABLE nodes (id NUMBER NOT NULL, name VARCHAR(56) NOT NULL, parent_id NUMBER)
-- BREAK
ALTER TABLE nodes ADD CONSTRAINT nodes_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE nodes_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER nodes_trigger
        BEFORE INSERT ON nodes REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, nodes_seq.nextval) into :new.id from dual;
end;


-- BREAK
CREATE TABLE apples (id NUMBER NOT NULL, apple_type VARCHAR(56) NOT NULL)
-- BREAK
ALTER TABLE apples ADD CONSTRAINT apples_pk PRIMARY KEY ( id )
-- BREAK


CREATE TABLE images (id NUMBER  NOT NULL , name VARCHAR(56) NOT NULL, content BLOB)
-- BREAK
ALTER TABLE images ADD CONSTRAINT images_pk PRIMARY KEY ( id )
-- BREAK
CREATE SEQUENCE images_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER images_trigger
        BEFORE INSERT ON images REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, images_seq.nextval) into :new.id from dual;
end;
-- BREAK


CREATE TABLE alarms (id NUMBER NOT NULL, alarm_time TIMESTAMP NOT NULL)
-- BREAK
ALTER TABLE alarms ADD CONSTRAINT alarms_pk PRIMARY KEY (id)
-- BREAK
CREATE SEQUENCE alarms_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER alarms_trigger
        BEFORE INSERT ON alarms REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, alarms_seq.nextval) into :new.id from dual;
end;
-- BREAK


CREATE TABLE developers (first_name VARCHAR(56) NOT NULL, last_name VARCHAR(56) NOT NULL, email VARCHAR(56) NOT NULL,address VARCHAR(56) NOT NULL)
-- BREAK
CREATE UNIQUE INDEX developers_uq ON developers (first_name, last_name, email)
-- BREAK



CREATE TABLE boxes (id NUMBER NOT NULL, color VARCHAR(56), fruit_id NUMBER)
-- BREAK
ALTER TABLE boxes ADD CONSTRAINT boxes_pk PRIMARY KEY (id)
-- BREAK
CREATE SEQUENCE boxes_seq START WITH 1 INCREMENT BY 1
-- BREAK
CREATE OR REPLACE TRIGGER boxes_trigger
        BEFORE INSERT ON alarms REFERENCING
        NEW AS new
        OLD AS old
        FOR EACH ROW
        begin
select coalesce(:new.id, boxes_seq.nextval) into :new.id from dual;
end;
-- BREAK
