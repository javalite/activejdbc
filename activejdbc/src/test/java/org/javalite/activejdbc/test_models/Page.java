package org.javalite.activejdbc.test_models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.conversion.ConverterAdapter;
import org.javalite.common.Convert;

/**
 * @author Igor Polevoy
 */
public class Page extends Model {
    public Page() {
        validateNumericalityOf("word_count").greaterThan(10).onlyInteger().message("'word_count' must be a number greater than 10");
        convertWith(new StringToIntegerConverter(), "word_count");
    }
    public static Page Page = new Page();
}

class StringToIntegerConverter extends ConverterAdapter<String, Integer> {

    @Override protected Class<String> sourceClass() { return String.class; }

    @Override protected Class<Integer> destinationClass() { return Integer.class; }

    @Override
    protected Integer doConvert(String source) throws Exception {
        return "zero".equals(source) ? 0 : Convert.toInteger(source);
    }
}
