package org.javalite.activejdbc;

import org.javalite.activejdbc.test.ActiveJDBCTest;
import org.javalite.activejdbc.test_models.Book;
import org.javalite.activejdbc.validation.exclusion.AttributeExclusionValidator;
import org.junit.Test;

import java.util.Locale;

public class AttributeExclusionValidatorTest extends ActiveJDBCTest {

    @Test
    public void shouldFailValidationWhenTitleIsInList() {
        final AttributeExclusionValidator invalidScienceFictionTitles =
                AttributeExclusionValidator.on("title").with("Dune", "Ender's Game");

        final Book book = new Book();
        book.set("title", "Dune");

        Book.addValidator(invalidScienceFictionTitles);
        book.validate();
        a(book.errors().size()).shouldBeEqual(1);
        Book.removeValidator(invalidScienceFictionTitles);
    }

    @Test
    public void shouldPassValidationWhenTitleIsNotInList() {
        final AttributeExclusionValidator invalidScienceFictionTitles =
                AttributeExclusionValidator.on("title").with("Dune", "Ender's Game");

        final Book book = new Book();
        book.set("title", "1984");

        Book.addValidator(invalidScienceFictionTitles);
        book.validate();
        a(book.errors().size()).shouldBeEqual(0);
        Book.removeValidator(invalidScienceFictionTitles);
    }

    @Test
    public void shouldHaveLocaleSpecificErrorMessageWhenFailingValidation() {
        final AttributeExclusionValidator invalidScienceFictionTitles =
                AttributeExclusionValidator.on("title").with("Dune", "Ender's Game");
        invalidScienceFictionTitles.setMessage("validation.inclusion");

        final Book book = new Book();
        book.set("title", "Dune");

        Book.addValidator(invalidScienceFictionTitles);
        book.validate();
        a(book.errors().size()).shouldBeEqual(1);
        a(book.errors().get("title")).shouldBeEqual("title is not included in the list.");
        a(book.errors(new Locale("de", "DE")).get("title")).shouldBeEqual("title ist nicht in der Liste enthalten.");
        Book.removeValidator(invalidScienceFictionTitles);
    }
}
