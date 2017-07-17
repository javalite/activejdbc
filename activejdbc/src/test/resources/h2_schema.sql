-- noinspection SqlNoDataSourceInspectionForFile
DROP TABLE IF EXISTS people;
CREATE TABLE people (id  int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL, last_name VARCHAR(56), dob DATE, graduation_date DATE, created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (id  int(11) NOT NULL auto_increment PRIMARY KEY, account VARCHAR(56), description VARCHAR(56), amount DECIMAL(10,2), total DECIMAL(10,2));

DROP TABLE IF EXISTS temperatures;
CREATE TABLE temperatures (id  int(11) NOT NULL  auto_increment PRIMARY KEY, temp SMALLINT);

DROP TABLE IF EXISTS shard1_temperatures;
CREATE TABLE shard1_temperatures (id  int(11) NOT NULL  auto_increment PRIMARY KEY, temp SMALLINT);

DROP TABLE IF EXISTS shard2_temperatures;
CREATE TABLE shard2_temperatures (id  int(11) NOT NULL  auto_increment PRIMARY KEY, temp SMALLINT);

DROP TABLE IF EXISTS salaries;
CREATE TABLE salaries (id  int(11) NOT NULL  auto_increment PRIMARY KEY, salary DECIMAL(7, 2));

DROP TABLE IF EXISTS users;
CREATE TABLE users (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56));

DROP TABLE IF EXISTS shard1_users;
CREATE TABLE shard1_users (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56));

DROP TABLE IF EXISTS addresses;
CREATE TABLE addresses (id  int(11) NOT NULL  auto_increment PRIMARY KEY, address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56), user_id int(11));

DROP TABLE IF EXISTS shard1_addresses;
CREATE TABLE shard1_addresses (id  int(11) NOT NULL  auto_increment PRIMARY KEY, address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56), user_id int(11));



DROP TABLE IF EXISTS rooms;
CREATE TABLE rooms (id  int(11) NOT NULL  auto_increment PRIMARY KEY, name VARCHAR(56), address_id int(11));

DROP TABLE IF EXISTS legacy_universities;
CREATE TABLE legacy_universities (id  int(11) NOT NULL  auto_increment PRIMARY KEY, univ_name VARCHAR(56), address1 VARCHAR(56), address2 VARCHAR(56), city VARCHAR(56), state VARCHAR(56), zip VARCHAR(56));

DROP TABLE IF EXISTS libraries;
CREATE TABLE libraries (id  int(11) NOT NULL  auto_increment PRIMARY KEY, address VARCHAR(56), city VARCHAR(56), state VARCHAR(56));

DROP TABLE IF EXISTS books;
CREATE TABLE books (id  int(11) NOT NULL  auto_increment PRIMARY KEY, title VARCHAR(56), author VARCHAR(56), isbn VARCHAR(56), lib_id int(11));

DROP TABLE IF EXISTS readers;
CREATE TABLE readers (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), book_id int(11));

DROP TABLE IF EXISTS animals;
CREATE TABLE animals (animal_id  int(11) NOT NULL  auto_increment PRIMARY KEY, animal_name VARCHAR(56));

DROP TABLE IF EXISTS patients;
CREATE TABLE patients (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56));

DROP TABLE IF EXISTS shard1_patients;
CREATE TABLE shard1_patients (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56));

DROP TABLE IF EXISTS prescriptions;
CREATE TABLE prescriptions (id  int(11) NOT NULL  auto_increment PRIMARY KEY, name VARCHAR(56), patient_id int(11));

DROP TABLE IF EXISTS doctors;
CREATE TABLE doctors (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), discipline varchar(56));

DROP TABLE IF EXISTS shard1_doctors;
CREATE TABLE shard1_doctors (id  int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), discipline varchar(56));

DROP TABLE IF EXISTS doctors_patients;
CREATE TABLE doctors_patients (id  int(11) NOT NULL  auto_increment PRIMARY KEY, doctor_id int(11), patient_id int(11));

DROP TABLE IF EXISTS students;
CREATE TABLE students (id  int(11) NOT NULL auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), dob DATE, enrollment_date DATETIME);

DROP TABLE IF EXISTS courses;
CREATE TABLE courses (id  int(11) NOT NULL  auto_increment PRIMARY KEY, course_name VARCHAR(56));

DROP TABLE IF EXISTS registrations;
CREATE TABLE registrations (id  int(11) NOT NULL  auto_increment PRIMARY KEY, astudent_id int(11), acourse_id int(11));


DROP TABLE IF EXISTS items;
CREATE TABLE items (id  int(11) NOT NULL  auto_increment PRIMARY KEY, item_number int(11), item_description VARCHAR(56), lock_version int(11));

DROP TABLE IF EXISTS articles;
CREATE TABLE articles (id  int(11) NOT NULL  auto_increment PRIMARY KEY, title VARCHAR(56), content TEXT);

DROP TABLE IF EXISTS shard1_articles;
CREATE TABLE shard1_articles (id  int(11) NOT NULL  auto_increment PRIMARY KEY, title VARCHAR(56), content TEXT);

DROP TABLE IF EXISTS posts;
CREATE TABLE posts (id  int(11) NOT NULL  auto_increment PRIMARY KEY, title VARCHAR(56), post TEXT);

DROP TABLE IF EXISTS shard1_posts;
CREATE TABLE shard1_posts (id  int(11) NOT NULL  auto_increment PRIMARY KEY, title VARCHAR(56), post TEXT);

DROP TABLE IF EXISTS comments;
CREATE TABLE comments (id  int(11) NOT NULL  auto_increment PRIMARY KEY, author VARCHAR(56), content TEXT, parent_id int(11), parent_type VARCHAR(256));

DROP TABLE IF EXISTS shard1_comments;
CREATE TABLE shard1_comments (id  int(11) NOT NULL  auto_increment PRIMARY KEY, author VARCHAR(56), content TEXT, parent_id int(11), parent_type VARCHAR(256));

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (id  int(11) NOT NULL  auto_increment PRIMARY KEY, content TEXT, parent_id int(11), parent_type VARCHAR(256));

DROP TABLE IF EXISTS fruits;
CREATE TABLE fruits (id  int(11) NOT NULL  auto_increment PRIMARY KEY, fruit_name VARCHAR(56), category VARCHAR(56), created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS vegetables;
CREATE TABLE vegetables (id  int(11) NOT NULL  auto_increment PRIMARY KEY, vegetable_name VARCHAR(56), category VARCHAR(56), created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS plants;
CREATE TABLE plants (id  int(11) NOT NULL  auto_increment PRIMARY KEY, plant_name VARCHAR(56), category VARCHAR(56), created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS pages;
CREATE TABLE pages ( id int(11) NOT NULL  auto_increment PRIMARY KEY, description VARCHAR(56), word_count int(11), "order" int(11) );


DROP TABLE IF EXISTS watermelons;
CREATE TABLE watermelons ( id int(11) NOT NULL  auto_increment PRIMARY KEY, melon_type VARCHAR(56), record_version INT, created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS schools;
CREATE TABLE schools ( id int(11) NOT NULL  auto_increment PRIMARY KEY, school_name VARCHAR(56), school_type VARCHAR(56), email VARCHAR(56), created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS programmers;
CREATE TABLE programmers ( id int(11) NOT NULL  auto_increment PRIMARY KEY, first_name VARCHAR(56), last_name VARCHAR(56), email VARCHAR(56), created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS projects;
CREATE TABLE projects ( id int(11) NOT NULL  auto_increment PRIMARY KEY, project_name VARCHAR(56), created_at DATETIME, updated_at DATETIME);

DROP TABLE IF EXISTS programmers_projects;
CREATE TABLE programmers_projects ( id int(11) NOT NULL  auto_increment PRIMARY KEY, duration_weeks int(3), project_id int(11), programmer_id int(11), created_at DATETIME, updated_at DATETIME);


DROP TABLE IF EXISTS computers;

DROP TABLE IF EXISTS keyboards;
CREATE TABLE keyboards ( id int(11) NOT NULL  auto_increment PRIMARY KEY, description VARCHAR(56));

DROP TABLE IF EXISTS motherboards;
CREATE TABLE motherboards ( id int(11) NOT NULL  auto_increment PRIMARY KEY, description VARCHAR(56));

CREATE TABLE computers ( id int(11) NOT NULL  auto_increment PRIMARY KEY, description VARCHAR(56), mother_id int(11), key_id int(11), constraint fk_computer_mother foreign key (mother_id) references motherboards(id), constraint fk_computer_key foreign key (key_id) references keyboards(id) );


DROP TABLE IF EXISTS ingredients;
CREATE TABLE ingredients (ingredient_id  int(11) NOT NULL  auto_increment PRIMARY KEY, ingredient_name VARCHAR(56));

DROP TABLE IF EXISTS recipes;
CREATE TABLE recipes (recipe_id  int(11) NOT NULL  auto_increment PRIMARY KEY, recipe_name VARCHAR(56));

DROP TABLE IF EXISTS ingredients_recipes;
CREATE TABLE ingredients_recipes (the_id  int(11) NOT NULL  auto_increment PRIMARY KEY, recipe_id int(11), ingredient_id int(11));


DROP TABLE IF EXISTS vehicles;
CREATE TABLE vehicles (id  int(11) NOT NULL  auto_increment PRIMARY KEY, name VARCHAR(56));

DROP TABLE IF EXISTS mammals;
CREATE TABLE mammals (id  int(11) NOT NULL  auto_increment PRIMARY KEY, name VARCHAR(56));

DROP TABLE IF EXISTS classifications;
CREATE TABLE classifications (id  int(11) NOT NULL  auto_increment PRIMARY KEY, name VARCHAR(56), parent_id int(11), parent_type VARCHAR(56));

DROP TABLE IF EXISTS sub_classifications;
CREATE TABLE sub_classifications (id  int(11) NOT NULL  auto_increment PRIMARY KEY, name VARCHAR(56), classification_id int(11));

DROP TABLE IF EXISTS content_groups;
create table content_groups ( id  int(11) NOT NULL  auto_increment PRIMARY KEY, group_name INT(11) );

DROP TABLE IF EXISTS cakes;
CREATE TABLE cakes (id int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL);

DROP TABLE IF EXISTS swords;
CREATE TABLE swords (id int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL);

DROP TABLE IF EXISTS meals;
CREATE TABLE meals (id int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL);

DROP TABLE IF EXISTS Member;
CREATE TABLE Member (id int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL);


DROP TABLE IF EXISTS nodes;
CREATE TABLE nodes (id int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL, parent_id int(11));

DROP TABLE IF EXISTS images;
CREATE TABLE images (id int(11) NOT NULL auto_increment PRIMARY KEY, name VARCHAR(56) NOT NULL, content BLOB);

DROP TABLE IF EXISTS apples;
CREATE TABLE apples (id int(11) NOT NULL PRIMARY KEY, apple_type VARCHAR(56) NOT NULL );

DROP TABLE IF EXISTS alarms;
CREATE TABLE alarms (id int(11) NOT NULL auto_increment PRIMARY KEY, alarm_time TIME NOT NULL);

DROP TABLE IF EXISTS developers;
CREATE TABLE developers (first_name VARCHAR(56) NOT NULL, last_name VARCHAR(56) NOT NULL, email VARCHAR(56) NOT NULL, address VARCHAR(56), CONSTRAINT developers_uq PRIMARY KEY (first_name, last_name, email));


DROP TABLE IF EXISTS "Wild Animals";
CREATE TABLE "Wild Animals" (id int(11) auto_increment PRIMARY KEY, "Name" VARCHAR(56) NOT NULL);

DROP TABLE IF EXISTS boxes;
CREATE TABLE boxes (id  int(11) NOT NULL auto_increment PRIMARY KEY, color VARCHAR(56) NOT NULL, fruit_id int(11), created_at DATETIME, updated_at DATETIME);
