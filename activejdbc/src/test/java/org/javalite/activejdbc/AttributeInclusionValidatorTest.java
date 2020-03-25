package org.javalite.activejdbc;

import org.javalite.activejdbc.test.ActiveJDBCTest;
import org.javalite.activejdbc.test_models.Book;
import org.javalite.activejdbc.validation.inclusion.AttributeInclusionValidator;
import org.junit.Test;

import java.util.Locale;

public class AttributeInclusionValidatorTest extends ActiveJDBCTest {

    @Test
    public void shouldPassValidationWhenTitleIsInList() {
        final AttributeInclusionValidator validScienceFictionTitles =
                AttributeInclusionValidator.on("title").with("Dune", "Ender's Game");

        final Book book = new Book();
        book.set("title", "Dune");

        Book.addValidator(validScienceFictionTitles);
        book.validate();
        a(book.errors().size()).shouldBeEqual(0);
        Book.removeValidator(validScienceFictionTitles);
    }

    @Test
    public void shouldFailValidationWhenTitleIsNotInList() {
        final AttributeInclusionValidator scienceFictionTitles =
                AttributeInclusionValidator.on("title").with("Dune", "Ender's Game");

        final Book book = new Book();
        book.set("title", "1984");

        Book.addValidator(scienceFictionTitles);
        book.validate();
        a(book.errors().size()).shouldBeEqual(1);
        Book.removeValidator(scienceFictionTitles);
    }

    @Test
    public void shouldHaveLocaleSpecificErrorMessageWhenFailingValidation() {
        final AttributeInclusionValidator validator =
                AttributeInclusionValidator.on("title").with("Dune", "Ender's Game");
        validator.setMessage("validation.inclusion");

        final Book book = new Book();
        book.set("title", "1984");

        Book.addValidator(validator);
        book.validate();
        a(book.errors().size()).shouldBeEqual(1);
        a(book.errors().get("title")).shouldBeEqual("title is not included in the list.");
        a(book.errors(new Locale("de", "DE")).get("title")).shouldBeEqual("title ist nicht in der Liste enthalten.");
        Book.removeValidator(validator);
    }
}
