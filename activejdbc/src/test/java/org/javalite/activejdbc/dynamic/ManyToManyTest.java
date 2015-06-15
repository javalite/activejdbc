/*
Copyright 2009-2014 Igor Polevoy

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


package org.javalite.activejdbc.dynamic;

import org.javalite.activejdbc.Base;
import org.javalite.activejdbc.Configuration;
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.ModelTemplate;
import org.javalite.activejdbc.Registry;
import org.javalite.activejdbc.associations.NotAssociatedException;
import org.javalite.activejdbc.test.ActiveJDBCTest;
import org.javalite.activejdbc.test_models.*;
import org.junit.Test;

import java.sql.Date;
import java.util.List;
import java.util.Map;


/**
 * @author Igor Polevoy
 */
public class ManyToManyTest extends ActiveJDBCTest {
       
    @Test
    public void shouldSelectManyToManyWithGetAllDynamic() throws ClassNotFoundException{
    	 deleteAndPopulateTables("doctors", "patients", "doctors_patients");
         Configuration config = Registry.instance().getConfiguration();
         
         
         Model doctor = Doctor.findById(1);
         List<Model> patients;
         boolean classNotFound = false;
         try {
        	 patients = doctor.getAll("aw.Patient");
         } catch (ClassNotFoundException ex) {
        	 classNotFound = true;
         }
         a(classNotFound).shouldBeTrue();
         
         //now add new models
         config.addModel("aw.Patient", new  Configuration.ModelAttributes("default"));
         config.addModel("aw.DoctorsPatients", new  Configuration.ModelAttributes("default"));
         Registry.instance().resetInitializedFlag("default");
         
         doctor = Doctor.findById(1);
         patients = doctor.getAll("aw.Patient");
         a(2).shouldBeEqual(patients.size());

         doctor = Doctor.findById(2);
         patients = doctor.getAll("aw.Patient");
         a(1).shouldBeEqual(patients.size());

         Model p = new ModelTemplate("aw.Patient").findById(1);
         List<Doctor> doctors = p.getAll(Doctor.class);
         a(2).shouldBeEqual(doctors.size());

         p = new ModelTemplate("aw.Patient").findById(2);
         doctors = p.getAll(Doctor.class);
         a(1).shouldBeEqual(doctors.size());
    }

    @Test
    public void shouldAddNewChildInManyToManyAssociation() throws ClassNotFoundException{
        deleteAndPopulateTables("doctors", "patients", "doctors_patients");
        Configuration config = Registry.instance().getConfiguration();
        config.addModel("aw.Patient", new  Configuration.ModelAttributes("default"));
        config.addModel("aw.DoctorsPatients", new  Configuration.ModelAttributes("default"));
        Registry.instance().resetInitializedFlag("default");
        

        
        Doctor doctor = Doctor.findById(1);
        Model jimThePatient = new ModelTemplate("aw.Patient").create("first_name", "Jim", "last_name", "Smith");
        ModelTemplate dpTemplate = new ModelTemplate("aw.DoctorsPatients");
        //this will add a new patient record and a new record in the join table that connects a doctor and a new patient.
        a(dpTemplate.count()).shouldBeEqual(4);
        doctor.add(jimThePatient);
        a(dpTemplate.count()).shouldBeEqual(5);

        List<Model> patients = doctor.getAll("aw.Patient").orderBy("patients.id");
        a(patients.size()).shouldBeEqual(3);
        a(patients.get(2).get("last_name")).shouldBeEqual("Smith");
    }

    @Test
    public void shouldSelectManyToManyWithGet() throws ClassNotFoundException{
        deleteAndPopulateTables("doctors", "patients", "doctors_patients");
        Configuration config = Registry.instance().getConfiguration();
        config.addModel("aw.Patient", new  Configuration.ModelAttributes("default"));
        config.addModel("aw.DoctorsPatients", new  Configuration.ModelAttributes("default"));
        Registry.instance().resetInitializedFlag("default");

        Doctor doctor = Doctor.findById(1);
        List<Model> patients = doctor.getAll("aw.Patient");
        a(2).shouldBeEqual(patients.size());

        doctor = Doctor.findById(2);
        patients = doctor.getAll("aw.Patient");
        a(1).shouldBeEqual(patients.size());

        Model p = new ModelTemplate("aw.Patient").findById(1);
        List<Doctor> doctors = p.getAll(Doctor.class);
        a(2).shouldBeEqual(doctors.size());

        p = new ModelTemplate("aw.Patient").findById(2);
        doctors = p.getAll(Doctor.class);
        a(1).shouldBeEqual(doctors.size());
    }
}
