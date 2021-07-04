/*
Copyright 2009-2019 Igor Polevoy

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/


package org.javalite.activejdbc;

import org.javalite.activejdbc.test.ActiveJDBCTest;
import org.javalite.activejdbc.test_models.Person;
import org.junit.Test;

/**
 * @author Igor Polevoy
 */
public class ModelExistsTest extends ActiveJDBCTest {

    @Test
    public void testStatic(){
        deleteAndPopulateTable("people");
        a(Person.exists(10000)).shouldBeFalse();
        a(Person.exists(1)).shouldBeTrue();        
    }

    @Test
    public void testInstance(){
        deleteAndPopulateTable("people");
        Person p = Person.<Person>findAll().get(0);
        Person.delete("id = ?", p.getId());
        a(p.exists()).shouldBeFalse();
    }

    @Test
    public void testExistsWithWhereClause(){
        deleteAndPopulateTable("people");
        a(Person.exists("id = ?", 10000)).shouldBeFalse();
        Person p = Person.<Person>findAll().get(0);
        a(Person.exists("id = ?", p.getId())).shouldBeTrue();
        a(Person.exists("name = ?", "John")).shouldBeTrue();
        a(Person.exists("name = ? and last_name = ?", "John", "Smith")).shouldBeTrue();
    }
}
