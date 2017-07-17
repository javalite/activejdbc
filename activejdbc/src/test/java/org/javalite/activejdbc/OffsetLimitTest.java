/*
Copyright 2009-2016 Igor Polevoy

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
import org.javalite.activejdbc.test_models.Page;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;


/**
 * @author Igor Polevoy
 */
public class OffsetLimitTest extends ActiveJDBCTest {

    @Before
    public void setup() throws Exception {
        deleteAndPopulateTable("pages");
        for (int i = 1; i <= 1000; i++) {
            Page.create("description", "description: " + i, "word_count", 11, "order", i).saveIt();
        }
    }

    @Test
    public void testAll() {

        List<Page> pages = Page.findAll().orderBy("id asc");


        //offset and limit
        pages = Page.findAll().offset(100).limit(20).orderBy("id");
        a(pages.size()).shouldBeEqual(20);
        a(pages.get(0).get("description")).shouldBeEqual("description: 101");
        a(pages.get(19).get("description")).shouldBeEqual("description: 120");

        //offset only
        pages = Page.findAll().limit(10000000).offset(990).orderBy("id");
        a(pages.size()).shouldBeEqual(10);
        a(pages.get(0).get("description")).shouldBeEqual("description: 991");
        a(pages.get(9).get("description")).shouldBeEqual("description: 1000");

        //limit only
        pages = Page.findAll().limit(20).orderBy("id");
        a(pages.get(0).get("description")).shouldBeEqual("description: 1");
        a(pages.get(9).get("description")).shouldBeEqual("description: 10");
        a(pages.get(19).get("description")).shouldBeEqual("description: 20");
        a(pages.size()).shouldBeEqual(20);
    }
}