package org.javalite.activejdbc.validation;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.test.ActiveJDBCTest;
import org.javalite.activejdbc.test_models.Person;
import org.javalite.validation.Validatable;
import org.javalite.validation.ValidatorAdapter;
import org.junit.Test;

import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * @author Igor Polevoy
 */
public class CustomValidatorTest extends ActiveJDBCTest {

    @Test
    public void shouldRegisterCustomValidator() {

        class FutureBirthValidator extends ValidatorAdapter {
            FutureBirthValidator(){
                setMessage("invalid.dob.message");
            }
            @Override
            public void validate(Validatable validatable) {

                Date dob = ((Model)validatable).getDate("dob");
                Date now = new java.sql.Date(System.currentTimeMillis());
                if(dob.after(now)){
                    validatable.addFailedValidator(this, "invalid.dob");//add validator to errors with a key
                }
            }
        }

        FutureBirthValidator validator = new FutureBirthValidator();

        Person.addValidator(validator);

        Person p = new Person();

        GregorianCalendar future = new GregorianCalendar();
        future.set(Calendar.YEAR, 3000);//will people still be using Java then... or computers? :)

        p.set("dob", new Date(future.getTimeInMillis()));
        p.validate();
        a(p.errors().size()).shouldBeEqual(3);

        a(p.errors().get("invalid.dob")).shouldBeEqual("date of birth cannot be in future");

        //this is so that other tests succeed
        Person.removeValidator(validator);
    }
}
