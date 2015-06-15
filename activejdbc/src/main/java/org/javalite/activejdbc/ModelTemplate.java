/*
Copyright 2009-2015 Igor Polevoy

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

import org.javalite.activejdbc.annotations.DbName;
import org.javalite.activejdbc.associations.*;
import org.javalite.activejdbc.cache.QueryCache;
import org.javalite.activejdbc.conversion.Converter;
import org.javalite.activejdbc.validation.NumericValidationBuilder;
import org.javalite.activejdbc.validation.ValidationBuilder;
import org.javalite.activejdbc.validation.ValidationException;
import org.javalite.activejdbc.validation.Validator;
import org.javalite.common.Convert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.*;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.*;

import org.javalite.activejdbc.conversion.BlankToNullConverter;
import org.javalite.activejdbc.conversion.ZeroToNullConverter;
import org.javalite.activejdbc.dialects.Dialect;
import org.javalite.common.Escape;
import org.javalite.common.Inflector.*;
import org.javalite.common.Util.*;

/**
 * This class is used to handle dynamic model (with no instrumentation).
 * All static methods of Model class are copied in this class and converted in to member methods (just removed static modifier).
 *
 * @author Abdul Wahid
 */


public class ModelTemplate {

    private final Logger logger = LoggerFactory.getLogger(ModelTemplate.class);
    private Class<? extends Model> modelClazz;

    public ModelTemplate(String className) throws ClassNotFoundException {
    	modelClazz = (Class<? extends Model>) Class.forName(className);
    }
    
    public MetaModel getMetaModel() {
        return ModelDelegate.metaModelOf(modelClass());
    }

    /**
     * Returns names of all attributes from this model.
     * @return names of all attributes from this model.
     * @deprecated use {@link #attributeNames()} instead
     */
    @Deprecated
    public List<String> attributes(){
        return ModelDelegate.attributes(modelClass());
    }

    /**
     * Returns names of all attributes from this model.
     * @return names of all attributes from this model.
     */
    public Set<String> attributeNames() {
        return ModelDelegate.attributeNames(modelClass());
    }

    /**
     * Returns all associations of this model.
     * @return all associations of this model.
     */
    public List<Association> associations() {
        return ModelDelegate.associations(modelClass());
    }

    /**
     * Deletes some records from associated table. This method does not follow any associations.
     * If this model has one to many associations, you might end up with either orphan records in child
     * tables, or run into integrity constraint violations. However, this method if very efficient as it deletes all records
     * in one shot, without pre-loading them.
     * This method also has a side-effect: it will not mark loaded instances corresponding to deleted records as "frozen".
     * This means that such an instance would allow calling save() and saveIt() methods resulting DB errors, as you
     * would be attempting to update phantom records.
     *
     *
     * @param query narrows which records to delete. Example: <pre>"last_name like '%sen%'"</pre>.
     * @param params   (optional) - list of parameters if a query is parametrized.
     * @return number of deleted records.
     */
    public int delete(String query, Object... params) {
        return ModelDelegate.delete(modelClass(), query, params);
    }

    /**
     * Returns true if record corresponding to the id passed exists in the DB.
     *
     * @param id id in question.
     * @return true if corresponding record exists in DB, false if it does not.
     */
    public boolean exists(Object id) {
        return ModelDelegate.exists(modelClass(), id);
    }

    /**
     * Deletes all records from associated table. This methods does not take associations into account.
     *
     * @return number of records deleted.
     */
    public int deleteAll() {
        return ModelDelegate.deleteAll(modelClass());
    }

    /**
     * Updates records associated with this model.
     *
     * This example :
     * <pre>
     *  Employee.update("bonus = ?", "years_at_company > ?", "5", "10");
     * </pre
     * In this example, employees who worked for more than 10 years, get a bonus of 5% (not so generous :)).
     *
     *
     * @param updates - what needs to be updated.
     * @param conditions specifies which records to update. If this argument is <code>null</code>, all records in table will be updated.
     * In such cases, use a more explicit {@link #updateAll(String, Object...)} method.
     * @param params list of parameters for both updates and conditions. Applied in the same order as in the arguments,
     * updates first, then conditions.
     * @return number of updated records.
     */
    public int update(String updates, String conditions, Object ... params) {
        return ModelDelegate.update(modelClass(), updates, conditions, params);
    }

    /**
     * Updates all records associated with this model.
     *
     * This example :
     * <pre>
     *  Employee.updateAll("bonus = ?", "10");
     * </pre>
     * In this example, all employees get a bonus of 10%.
     *
     *
     * @param updates - what needs to be updated.
     * @param params list of parameters for both updates and conditions. Applied in the same order as in the arguments,
     * updates first, then conditions.
     * @return number of updated records.
     */
    public int updateAll(String updates, Object ... params) {
        return ModelDelegate.updateAll(modelClass(), updates, params);
    }

    protected NumericValidationBuilder validateNumericalityOf(String... attributeNames) {
        return ModelDelegate.validateNumericalityOf(modelClass(), attributeNames);
    }

    /**
     * Adds a validator to the model.
     *
     * @param validator new validator.
     */
    public ValidationBuilder addValidator(Validator validator) {
        return ModelDelegate.validateWith(modelClass(), validator);
    }

    public void removeValidator(Validator validator){
        ModelDelegate.removeValidator(modelClass(), validator);
    }

    //TODO: missing no-arg getValidators()?
    public List<Validator> getValidators(Class<? extends Model> clazz) {
        return ModelDelegate.validatorsOf(clazz);
    }

    /**
     * Validates an attribite format with a ree hand regular expression.
     *
     * @param attributeName attribute to validate.
     * @param pattern regexp pattern which must match  the value.
     * @return
     */
    protected ValidationBuilder validateRegexpOf(String attributeName, String pattern) {
        return ModelDelegate.validateRegexpOf(modelClass(), attributeName, pattern);
    }

    /**
     * Validates email format.
     *
     * @param attributeName name of attribute that holds email value.
     * @return
     */
    protected ValidationBuilder validateEmailOf(String attributeName) {
        return ModelDelegate.validateEmailOf(modelClass(), attributeName);
    }

    /**
     * Validates range. Accepted types are all java.lang.Number subclasses:
     * Byte, Short, Integer, Long, Float, Double BigDecimal.
     *
     * @param attributeName attribute to validate - should be within range.
     * @param min min value of range.
     * @param max max value of range.
     * @return
     */
    protected ValidationBuilder validateRange(String attributeName, Number min, Number max) {
        return ModelDelegate.validateRange(modelClass(), attributeName, min, max);
    }

    /**
     * The validation will not pass if the value is either an empty string "", or null.
     *
     * @param attributeNames list of attributes to validate.
     * @return
     */
    protected ValidationBuilder validatePresenceOf(String... attributeNames) {
        return ModelDelegate.validatePresenceOf(modelClass(), attributeNames);
    }

    /**
     * Add a custom validator to the model.
     *
     * @param validator  custom validator.
     */
    protected ValidationBuilder validateWith(Validator validator) {
        return ModelDelegate.validateWith(modelClass(), validator);
    }

    /**
     * Adds a custom converter to the model.
     *
     * @param converter custom converter
     * @deprecated use {@link #convertWith(org.javalite.activejdbc.conversion.Converter, String...)} instead
     */
    @Deprecated
    protected ValidationBuilder convertWith(org.javalite.activejdbc.validation.Converter converter) {
        return ModelDelegate.convertWith(modelClass(), converter);
    }

    /**
     * Registers a custom converter for the specified attributes.
     *
     * @param converter custom converter
     * @param attributeNames attribute names
     */
    protected void convertWith(Converter converter, String... attributeNames) {
        ModelDelegate.convertWith(modelClass(), converter, attributeNames);
    }

    /**
     * Converts a named attribute to <code>java.sql.Date</code> if possible.
     * Acts as a validator if cannot make a conversion.
     *
     * @param attributeName name of attribute to convert to <code>java.sql.Date</code>.
     * @param format format for conversion. Refer to {@link java.text.SimpleDateFormat}
     * @return message passing for custom validation message.
     * @deprecated use {@link #dateFormat(String, String...) instead
     */
    @Deprecated
    protected ValidationBuilder convertDate(String attributeName, String format){
        return ModelDelegate.convertDate(modelClass(), attributeName, format);
    }

    /**
     * Converts a named attribute to <code>java.sql.Timestamp</code> if possible.
     * Acts as a validator if cannot make a conversion.
     *
     * @param attributeName name of attribute to convert to <code>java.sql.Timestamp</code>.
     * @param format format for conversion. Refer to {@link java.text.SimpleDateFormat}
     * @return message passing for custom validation message.
     * @deprecated use {@link #timestampFormat(String, String...) instead
     */
    @Deprecated
    protected ValidationBuilder convertTimestamp(String attributeName, String format){
        return ModelDelegate.convertTimestamp(modelClass(), attributeName, format);
    }

    /**
     * Registers date format for specified attributes. This format will be used to convert between
     * Date -> String -> java.sql.Date when using the appropriate getters and setters.
     *
     * <p>For example:
     * <blockquote><pre>
     * public class Person extends Model {
     *     {
     *         dateFormat("MM/dd/yyyy", "dob");
     *     }
     * }
     *
     * Person p = new Person();
     * // will convert String -> java.sql.Date
     * p.setDate("dob", "02/29/2000");
     * // will convert Date -> String, if dob value in model is of type Date
     * String str = p.getString("dob");
     *
     * // will convert Date -> String
     * p.setString("dob", new Date());
     * // will convert String -> java.sql.Date, if dob value in model is of type String
     * Date date = p.getDate("dob");
     * </pre></blockquote>
     *
     * @param pattern pattern to use for conversion
     * @param attributeNames attribute names
     */
    protected void dateFormat(String pattern, String... attributeNames) {
        ModelDelegate.dateFormat(modelClass(), pattern, attributeNames);
    }

    /**
     * Registers date format for specified attributes. This format will be used to convert between
     * Date -> String -> java.sql.Date when using the appropriate getters and setters.
     *
     * <p>See example in {@link #dateFormat(String, String...)}.
     *
     * @param format format to use for conversion
     * @param attributeNames attribute names
     */
    protected void dateFormat(DateFormat format, String... attributeNames) {
        ModelDelegate.dateFormat(modelClass(), format, attributeNames);
    }

    /**
     * Registers date format for specified attributes. This format will be used to convert between
     * Date -> String -> java.sql.Timestamp when using the appropriate getters and setters.
     *
     * <p>For example:
     * <blockquote><pre>
     * public class Person extends Model {
     *     {
     *         timestampFormat("MM/dd/yyyy hh:mm a", "birth_datetime");
     *     }
     * }
     *
     * Person p = new Person();
     * // will convert String -> java.sql.Timestamp
     * p.setTimestamp("birth_datetime", "02/29/2000 12:07 PM");
     * // will convert Date -> String, if dob value in model is of type Date or java.sql.Timestamp
     * String str = p.getString("birth_datetime");
     *
     * // will convert Date -> String
     * p.setString("birth_datetime", new Date());
     * // will convert String -> java.sql.Timestamp, if dob value in model is of type String
     * Timestamp ts = p.getTimestamp("birth_datetime");
     * </pre></blockquote>
     *
     * @param pattern pattern to use for conversion
     * @param attributeNames attribute names
     */
    protected void timestampFormat(String pattern, String... attributeNames) {
        ModelDelegate.timestampFormat(modelClass(), pattern, attributeNames);
    }

    /**
     * Registers date format for specified attributes. This format will be used to convert between
     * Date -> String -> java.sql.Timestamp when using the appropriate getters and setters.
     *
     * <p>See example in {@link #timestampFormat(String, String...)}.
     *
     * @param format format to use for conversion
     * @param attributeNames attribute names
     */
    protected void timestampFormat(DateFormat format, String... attributeNames) {
        ModelDelegate.timestampFormat(modelClass(), format, attributeNames);
    }

    /**
     * Registers {@link BlankToNullConverter} for specified attributes. This will convert instances of <tt>String</tt>
     * that are empty or contain only whitespaces to <tt>null</tt>.
     *
     * @param attributeNames attribute names
     */
    protected void blankToNull(String... attributeNames) {
        ModelDelegate.blankToNull(modelClass(), attributeNames);
    }

    /**
     * Registers {@link ZeroToNullConverter} for specified attributes. This will convert instances of <tt>Number</tt>
     * that are zero to <tt>null</tt>.
     *
     * @param attributeNames attribute names
     */
    protected void zeroToNull(String... attributeNames) {
        ModelDelegate.zeroToNull(modelClass(), attributeNames);
    }

    public boolean belongsTo(Class<? extends Model> targetClass) {
        return ModelDelegate.belongsTo(modelClass(), targetClass);
    }

    /**
     * @deprecated use {@link #callbackWith(CallbackListener...)} instead
     */
    @Deprecated
    public void addCallbacks(CallbackListener... listeners) {
         ModelDelegate.callbackWith(modelClass(), listeners);
    }

    public void callbackWith(CallbackListener... listeners) {
         ModelDelegate.callbackWith(modelClass(), listeners);
    }

    /**
     * This is a convenience method to create a model instance already initialized with values.
     * Example:
     * <pre>
     * Person p = Person.create("name", "Sam", "last_name", "Margulis", "dob", "2001-01-07");
     * </pre>
     *
     * The first (index 0) and every other element in the array is an attribute name, while the second (index 1) and every
     * other is a corresponding value.
     *
     * This allows for better readability of code. If you just read this aloud, it will become clear.
     *
     * @param namesAndValues names and values. elements at indexes 0, 2, 4, 8... are attribute names, and elements at
     * indexes 1, 3, 5... are values. Element at index 1 is a value for attribute at index 0 and so on.
     * @return newly instantiated model.
     */
    public <T extends Model> T create(Object... namesAndValues) {
        return (T) ModelDelegate.create(modelClazz, namesAndValues);
    }

    /**
     * This is a convenience method to {@link #create(Object...)}. It will create a new model and will save it
     * to DB. It has the same semantics as {@link #saveIt()}.
     *
     * @param namesAndValues names and values. elements at indexes 0, 2, 4, 8... are attribute names, and elements at
     * indexes 1, 3, 5... are values. Element at index 1 is a value for attribute at index 0 and so on.
     * @return newly instantiated model which also has been saved to DB.
     */
    public <T extends Model> T createIt(Object ... namesAndValues){
        return (T) ModelDelegate.createIt(modelClazz, namesAndValues);
    }

    public <T extends Model> T findById(Object id) {
        return (T) ModelDelegate.findById(modelClazz, id);
    }

    /**
     * Finder method for DB queries based on table represented by this model. Usually the SQL starts with:
     *
     * <code>"select * from table_name where " + subquery</code> where table_name is a table represented by this model.
     *
     * Code example:
     * <pre>
     *
     * List<Person> teenagers = Person.where("age &gt ? and age &lt ?", 12, 20);
     * // iterate...
     *
     * //same can be achieved (since parameters are optional):
     * List<Person> teenagers = Person.where("age &gt 12 and age &lt 20");
     * //iterate
     * </pre>
     *
     * Limit, offset and order by can be chained like this:
     *
     * <pre>
     * List<Person> teenagers = Person.where("age &gt ? and age &lt ?", 12, 20).offset(101).limit(20).orderBy(age);
     * //iterate
     * </pre>
     *
     * This is a great way to build paged applications.
     *
     *
     * @param subquery this is a set of conditions that normally follow the "where" clause. Example:
     * <code>"department = ? and dob > ?"</code>. If this value is "*" and no parameters provided, then {@link #findAll()} is executed.
     * @param params list of parameters corresponding to the place holders in the subquery.
     * @return instance of <code>LazyList<Model></code> containing results.
     */
    public <T extends Model> LazyList<T> where(String subquery, Object... params) {
        return (LazyList<T>) ModelDelegate.where(modelClazz, subquery, params);
    }

    /**
     * Synonym of {@link #where(String, Object...)}
     *
     * @param subquery this is a set of conditions that normally follow the "where" clause. Example:
     * <code>"department = ? and dob &gt ?"</code>. If this value is "*" and no parameters provided, then {@link #findAll()} is executed.
     * @param params list of parameters corresponding to the place holders in the subquery.
     * @return instance of <code>LazyList<Model></code> containing results.
     */
    public <T extends Model> LazyList<T> find(String subquery, Object... params) {
        return (LazyList<T>) ModelDelegate.where(modelClazz, subquery, params);
    }

    /**
     * Synonym of {@link #first(String, Object...)}.
     *
     * @param subQuery selection criteria, example:
     * <pre>
     * Person johnTheTeenager = Person.findFirst("name = ? and age &gt 13 and age &lt 19 order by age", "John")
     * </pre>
     * Sometimes a query might be just a clause like this:
     * <pre>
     * Person oldest = Person.findFirst("order by age desc")
     * </pre>
     * @param params list of parameters if question marks are used as placeholders
     * @return a first result for this condition. May return null if nothing found.
     */
    public <T extends Model> T findFirst(String subQuery, Object... params) {
        return (T) ModelDelegate.findFirst(modelClazz, subQuery, params);
    }

    /**
     * Returns a first result for this condition. May return null if nothing found.
     * If last result is needed, then order by some field and call this nethod:
     *
     * Synonym of {@link #findFirst(String, Object...)}.
     * <pre>
     * //first:
     * Person youngestTeenager= Person.first("age &gt 12 and age &lt 20 order by age");
     *
     * //last:
     * Person oldestTeenager= Person.first("age &gt 12 and age &lt 20 order by age desc");
     * </pre>
     *
     *
     * @param subQuery selection criteria, example:
     * <pre>
     * Person johnTheTeenager = Person.first("name = ? and age &lt 13 order by age", "John")
     * </pre>
     * Sometimes a query might be just a clause like this:
     * <pre>
     * Person p = Person.first("order by age desc")
     * </pre>
     * @param params list of parameters if question marks are used as placeholders
     * @return a first result for this condition. May return null if nothing found.
     */
    public <T extends Model> T first(String subQuery, Object... params) {
        return (T) ModelDelegate.findFirst(modelClazz, subQuery, params);
    }

    /**
     * This method is for processing really large result sets. Results found by this method are never cached.
     *
     * @param query query text.
     * @param listener this is a call back implementation which will receive instances of models found.
     * @deprecated use {@link #findWith(ModelListener, String, Object...)}.
     */
    @Deprecated
    public void find(String query, final ModelListener listener) {
        ModelDelegate.findWith(modelClass(), listener, query);
    }

    /**
     * This method is for processing really large result sets. Results found by this method are never cached.
     *
     * @param listener this is a call back implementation which will receive instances of models found.
     * @param query sub-query (content after "WHERE" clause)
     * @param params optional parameters for a query.
     */
    public void findWith(final ModelListener listener, String query, Object ... params) {
        ModelDelegate.findWith(modelClass(), listener, query, params);
    }

    /**
     * Free form query finder. Example:
     * <pre>
     * List<Rule> rules = Rule.findBySQL("select rule.*, goal_identifier from rule, goal where goal.goal_id = rule.goal_id order by goal_identifier asc, rule_type desc");
     * </pre>
     * Ensure that the query returns all columns associated with this model, so that the resulting models could hydrate itself properly.
     * Returned columns that are not part of this model will be ignored, but can be used for caluses like above.
     *
     * @param fullQuery free-form SQL.
     * @param params parameters if query is parametrized.
     * @param <T> - class that extends Model.
     * @return list of models representing result set.
     */
    public <T extends Model> LazyList<T> findBySQL(String fullQuery, Object... params) {
        return (LazyList<T>) ModelDelegate.findBySql(modelClazz, fullQuery, params);
    }

    /**
     * This method returns all records from this table. If you need to get a subset, look for variations of "find()".
     *
     * @return result list
     */
    public <T extends Model> LazyList<T> findAll() {
        return (LazyList<T>) ModelDelegate.findAll(modelClazz);
    }

    /**
     * Returns total count of records in table.
     *
     * @return total count of records in table.
     */
    public Long count() {
        return ModelDelegate.count(modelClass());
    }

    /**
     * Returns count of records in table under a condition.
     *
     * @param query query to select records to count.
     * @param params parameters (if any) for the query.
     * @return count of records in table under a condition.
     */
    public Long count(String query, Object... params) {
        return ModelDelegate.count(modelClass(), query, params);
    }

    private <T extends Model> Class<T> modelClass() {
        return (Class<T>) modelClazz;
    }

    public String getTableName() {
        return ModelDelegate.tableNameOf(modelClass());
    }

}
