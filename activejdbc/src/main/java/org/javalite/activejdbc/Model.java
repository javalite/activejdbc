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

import static org.javalite.common.Inflector.*;
import static org.javalite.common.Util.*;

/**
 * This class is a super class of all "models" and provides most functionality
 * necessary for implementation of Active Record pattern.
 *
 * @author Igor Polevoy
 * @author Eric Nielsen
 */
public abstract class Model extends CallbackSupport implements Externalizable {

    private final static Logger logger = LoggerFactory.getLogger(Model.class);

    private Map<String, Object> attributes = new CaseInsensitiveMap<Object>();
    private final Set<String> dirtyAttributeNames = new CaseInsensitiveSet();
    private boolean frozen = false;
    private MetaModel metaModelLocal;
    private ModelRegistry modelRegistryLocal;
    private final Map<Class, Model> cachedParents = new HashMap<Class, Model>();
    private final Map<Class, List<Model>> cachedChildren = new HashMap<Class, List<Model>>();
    private boolean manageTime = true;

    private Errors errors = new Errors();

    protected Model() {
    }

    private void fireAfterLoad() {
        afterLoad();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.afterLoad(this);
        }
    }

    private void fireBeforeSave() {
        beforeSave();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.beforeSave(this);
        }
    }

    private void fireAfterSave() {
        afterSave();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.afterSave(this);
        }
    }

    private void fireBeforeCreate() {
        beforeCreate();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.beforeCreate(this);
        }
    }

    private void fireAfterCreate() {
        afterCreate();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.afterCreate(this);
        }
    }

    private void fireBeforeUpdate() {
        beforeUpdate();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.beforeUpdate(this);
        }
    }
    
    private void fireAfterUpdate() {
        afterUpdate();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.afterUpdate(this);
        }
    }
    
    private void fireBeforeDelete() {
        beforeDelete();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.beforeDelete(this);
        }
    }

    private void fireAfterDelete() {
        afterDelete();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.afterDelete(this);
        }
    }

    private void fireBeforeValidation() {
        beforeValidation();
        for(CallbackListener callback: modelRegistryLocal().callbacks())
            callback.beforeValidation(this);
    }

    private void fireAfterValidation() {
        afterValidation();
        for (CallbackListener callback : modelRegistryLocal().callbacks()) {
            callback.afterValidation(this);
        }
    }

    public static MetaModel getMetaModel() {
        return ModelDelegate.metaModelOf(modelClass());
    }

    protected Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
    
    protected Set<String> dirtyAttributeNames() {
        return Collections.unmodifiableSet(dirtyAttributeNames);
    }

    /**
     * Overrides attribute values from input map. The input map may have attributes whose name do not match the
     * attribute names (columns) of this model. Such attributes will be ignored. Those values whose names are
     * not present in the argument map, will stay untouched. The input map may have only partial list of attributes.
     *
     * @param input map with attributes to overwrite this models'. Keys are names of attributes of this model, values
     * are new values for it.
     */
    public <T extends Model> T fromMap(Map input) {
        hydrate(input, false);
        dirtyAttributeNames.addAll(input.keySet());
        return (T) this;
    }



    /**
     * Hydrates a this instance of model from a map. Only picks values from a map that match
     * this instance's attribute names, while ignoring the others.
     *
     * @param attributesMap map containing values for this instance.
     */
    protected void hydrate(Map<String, Object> attributesMap, boolean fireAfterLoad) {
        Set<String> attributeNames = getMetaModelLocal().getAttributeNames();
        for (Map.Entry<String, Object> entry : attributesMap.entrySet()) {
            if (attributeNames.contains(entry.getKey())) {
                if (entry.getValue() instanceof Clob && getMetaModelLocal().cached()) {
                    this.attributes.put(entry.getKey(), Convert.toString(entry.getValue()));
                } else {
                    this.attributes.put(entry.getKey(), getMetaModelLocal().getDialect().overrideDriverTypeConversion(
                            getMetaModelLocal(), entry.getKey(), entry.getValue()));
                }
            }
        }

        if(fireAfterLoad){
            fireAfterLoad();
        }

    }


    /**
     * Convenience method, sets ID value on this model, equivalent to <code>set(getIdName(), id)</code>.
     *
     * @param id value of ID
     * @return reference to self for chaining.
     */
    public <T extends Model> T setId(Object id) {
        return set(getIdName(), id);
    }

    /**
     * Sets attribute value as <code>java.sql.Date</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.sql.Date</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toSqlDate(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value to convert.
     * @return reference to this model.
     */
    public <T extends Model> T setDate(String attributeName, Object value) {
        Converter<Object, java.sql.Date> converter = modelRegistryLocal().converterForValue(
                attributeName, value, java.sql.Date.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toSqlDate(value));
    }

    /**
     * Gets attribute value as <code>java.sql.Date</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.sql.Date</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toSqlDate(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>java.sql.Date</code>
     */
    public java.sql.Date getDate(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, java.sql.Date> converter = modelRegistryLocal().converterForValue(
                attributeName, value, java.sql.Date.class);
        return converter != null ? converter.convert(value) : Convert.toSqlDate(value);
    }

    /**
     * Performs a primitive conversion of <code>java.util.Date</code> to <code>java.sql.Timestamp</code>
     * based on the time value.
     *
     * @param name name of field.
     * @param date date value.
     * @deprecated  use {@link #setTimestamp(String, Object)} instead.
     */
    @Deprecated
    public void setTS(String name, java.util.Date date) {
        if(date == null) {
            set(name, null);
        } else {
            set(name, new java.sql.Timestamp(date.getTime()));
        }
    }

    /**
     * Sets values for this model instance. The sequence of values must correspond to sequence of names.
     *
     * @param attributeNames names of attributes.
     * @param values values for this instance.
     */
    public void set(String[] attributeNames, Object[] values) {
        if (attributeNames == null || values == null || attributeNames.length != values.length) {
            throw new IllegalArgumentException("must pass non-null arrays of equal length");
        }

        for (int i = 0; i < attributeNames.length; i++) {
            set(attributeNames[i], values[i]);
        }
    }

    /**
     * Sets a value of an attribute.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Object</code>, given the value is an instance of <code>S</code>, then it will be used and the
     * converted value will be set.
     *
     * @param attributeName name of attribute to set. Names not related to this model will be rejected (those not matching table columns).
     * @param value value of attribute. Feel free to set any type, as long as it can be accepted by your driver.
     * @return reference to self, so you can string these methods one after another.
     */
    public <T extends Model> T set(String attributeName, Object value) {
        Converter<Object, Object> converter = modelRegistryLocal().converterForValue(attributeName, value, Object.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : value);
    }

    /**
     * Sets raw value of an attribute, without applying conversions.
     */
    private <T extends Model> T setRaw(String attributeName, Object value) {
        if (manageTime && attributeName.equalsIgnoreCase("created_at")) {
            throw new IllegalArgumentException("cannot set 'created_at'");
        }
        getMetaModelLocal().checkAttributeOrAssociation(attributeName);

        attributes.put(attributeName, value);
        dirtyAttributeNames.add(attributeName);
        return (T) this;
    }

    /**
     * Will return true if any attribute of this instance was changed after latest load/save.
     * (Instance state differs from state in DB)
     * @return true if this instance was modified.
     */
    public boolean isModified() {
        return !dirtyAttributeNames.isEmpty();
    }
    
    /**
     * Will return true if this  instance is frozen, false otherwise.
     * A frozen instance cannot use used, as it has no relation to a record in table.
     *
     * @return true if this  instance is frozen, false otherwise.
     */
    public boolean isFrozen(){
        return frozen;
    }

    /**
     * Synonym for {@link #isModified()}.
     *
     * @return true if this instance was modified.
     */
    public boolean modified() {
        return isModified();
    }

    /**
     * Returns names of all attributes from this model.
     * @return names of all attributes from this model.
     * @deprecated use {@link #attributeNames()} instead
     */
    @Deprecated
    public static List<String> attributes(){
        return ModelDelegate.attributes(modelClass());
    }

    /**
     * Returns names of all attributes from this model.
     * @return names of all attributes from this model.
     */
    public static Set<String> attributeNames() {
        return ModelDelegate.attributeNames(modelClass());
    }

    /**
     * Returns all associations of this model.
     * @return all associations of this model.
     */
    public static List<Association> associations() {
        return ModelDelegate.associations(modelClass());
    }

    /**
     * returns true if this is a new instance, not saved yet to DB, false otherwise.
     *
     * @return true if this is a new instance, not saved yet to DB, false otherwise
     */
    public boolean isNew(){
        return getId() == null;
    }


    /**
     * Synonym for {@link #isFrozen()}. if(m.frozen()) seems to read better than classical Java convention.
     *
     * @return true if this  instance is frozen, false otherwise.
     */
    public boolean frozen(){
        return isFrozen();
    }

    /**
     * Deletes a single table record represented by this instance. This method assumes that a corresponding table
     * has only one record whose PK is the ID of this instance.
     * After deletion, this instance becomes {@link #frozen()} and cannot be used anymore until {@link #thaw()} is called.
     *
     * @return true if a record was deleted, false if not.
     */
    public boolean delete() {
        fireBeforeDelete();
        boolean result;
        if( 1 == new DB(getMetaModelLocal().getDbName()).exec("DELETE FROM " + getMetaModelLocal().getTableName()
                + " WHERE " + getIdName() + "= ?", getId())) {

            frozen = true;
            if(getMetaModelLocal().cached()){
                QueryCache.instance().purgeTableCache(getMetaModelLocal().getTableName());
            }
            ModelDelegate.purgeEdges(getMetaModelLocal());
            result = true;
        }
        else{
            result =  false;
        }
        fireAfterDelete();
        return result;
    }


    /**
     * Convenience method, will call {@link #delete()} or {@link #deleteCascade()}.
     *
     * @param cascade true to call {@link #deleteCascade()}, false to call {@link #delete()}.
     */
    public void delete(boolean cascade){
       if(cascade){
           deleteCascade();
       }else{
           delete();
       }
    }

    /**
     * Deletes this record from associated table, as well as children.
     *
     * Deletes current model and all of its child and many to many associations. This is not a high performance method, as it will
     * load every row into a model instance before deleting, effectively calling (N + 1) per table queries to the DB, one to select all
     * the associated records (per table), and one delete statement per record. Use it for small data sets.
     *
     * <p/>
     * In cases of simple one to many and polymorphic associations, things are as expected, a parent is deleted an all children are
     * deleted as well, but in more complicated cases, this method will walk entire three of associated tables, sometimes
     * coming back to the same one where it all started.
     * It will follow associations of children and their associations too; consider this a true cascade delete with all implications
     * (circular dependencies, referential integrity constraints, potential performance bottlenecks, etc.)
     * <p/>
     *
     * Imagine a situation where you have DOCTORS and PATIENTS in many to many relationship (with DOCTORS_PATIENTS table
     * as a join table), and in addition PATIENTS and PRESCRIPTIONS in one to many relationship, where a patient might
     * have many prescriptions:
     *
     <pre>
     DOCTORS
        +----+------------+-----------+-----------------+
        | id | first_name | last_name | discipline      |
        +----+------------+-----------+-----------------+
        |  1 | John       | Kentor    | otolaryngology  |
        |  2 | Hellen     | Hunt      | dentistry       |
        |  3 | John       | Druker    | oncology        |
        +----+------------+-----------+-----------------+

     PATIENTS
        +----+------------+-----------+
        | id | first_name | last_name |
        +----+------------+-----------+
        |  1 | Jim        | Cary      |
        |  2 | John       | Carpenter |
        |  3 | John       | Doe       |
        +----+------------+-----------+

     DOCTORS_PATIENTS
        +----+-----------+------------+
        | id | doctor_id | patient_id |
        +----+-----------+------------+
        |  1 |         1 |          2 |
        |  2 |         1 |          1 |
        |  3 |         2 |          1 |
        |  4 |         3 |          3 |
        +----+-----------+------------+

     PRESCRIPTIONS
        +----+------------------------+------------+
        | id | name                   | patient_id |
        +----+------------------------+------------+
        |  1 | Viagra                 |          1 |
        |  2 | Prozac                 |          1 |
        |  3 | Valium                 |          2 |
        |  4 | Marijuana (medicinal)  |          2 |
        |  5 | CML treatment          |          3 |
        +----+------------------------+------------+
     * </pre>
     *
     * Lets start with a simple example, Doctor John Druker. This doctor has one patient John Doe, and the patient has one prescription.
     * So, when an instance of this doctor model is issued statement:
     * <pre>
     *     drDruker.deleteCascade();
     * </pre>
     * , the result is as expected: the DOCTORS:ID=3 is deleted, DOCTORS_PATIENTS:ID=4 is deleted, PATIENTS:ID=3 is deleted
     * and PRESCRIPTIONS:ID=5 is deleted.
     *
     * <p/>
     * However, when doctor Kentor(#1) is deleted, the following records are also deleted:
     * <ul>
     *     <li>DOCTORS_PATIENTS:ID=1, 2 - these are links to patients</li>
     *     <li>PATIENTS:ID=1,2 these are patients themselves</li>
     *     <li>PRESCRIPTIONS:ID=1,2,3,4  - these are prescriptions of patients 1 and 2</li>
     * </ul>
     * But, in addition, since this is a many to many relationship, deleting patients 1 and 2 results in also deleting
     * doctor Hellen Hunt(#2), since she is a doctor of patient Jim Cary(#1), deleting all corresponding join links from
     * table DOCTORS_PATIENTS. So, deleting doctor Kentor, deleted most all records from related tables, leaving only these
     * records in place:
     * <ul>
     *     <li>DOCTORS:ID=3</li>
     *     <li>DOCTORS_PATIENTS:ID=4</li>
     *     <li>PATIENTS:ID=3</li>
     *     <li>PRESCRIPTIONS:ID=5</li>
     * </ul>
     * Had doctor Hellen Hunt(#2) had more patients, it would delete them too, and so on. This goes a long way to say that it
     * could be easy to be tangled up in web of associations, so be careful out there.
     *
     * <p/>
     * After deletion, this instance becomes {@link #frozen()} and cannot be used anymore until {@link #thaw()} is called.
     */
    public void deleteCascade(){
        deleteCascadeExcept();
    }

    /**
     * This method does everything {@link #deleteCascade()} does, but in addition allows to exclude some associations
     * from this action. This is necessary because {@link #deleteCascade()} method can be far too eager to delete
     * records in a database, and this is a good way to tell the model to exclude some associations from deletes.
     *
     * <p>Example:</p>
     * <code>
     *     Patient.findById(3).deleteCascadeExcept(Patient.getMetaModel().getAssociationForTarget("prescriptions"));
     * </code>
     *
     * @see {@link #deleteCascade()} - see for more information.
     * @param excludedAssociations associations
     */
    public void deleteCascadeExcept(Association ... excludedAssociations){
        List<Association> excludedAssociationsList = Arrays.asList(excludedAssociations);
        deleteMany2ManyDeep(getMetaModelLocal().getManyToManyAssociations(excludedAssociationsList));
        deleteChildrenDeep(getMetaModelLocal().getOneToManyAssociations(excludedAssociationsList));
        deleteChildrenDeep(getMetaModelLocal().getPolymorphicAssociations(excludedAssociationsList));
        delete();
    }



    private void deleteMany2ManyDeep(List<Many2ManyAssociation> many2ManyAssociations){
        List<Model>  allMany2ManyChildren = new ArrayList<Model>();
        for (Association association : many2ManyAssociations) {
            String targetTableName = association.getTarget();
            Class c = Registry.instance().getModelClass(targetTableName, false);
            if(c == null){// this model is probably not defined as a class, but the table exists!
                logger.error("ActiveJDBC WARNING: failed to find a model class for: {}, maybe model is not defined for this table?"
                        + " There might be a risk of running into integrity constrain violation if this model is not defined.",
                        targetTableName);
            }
            else{
                allMany2ManyChildren.addAll(getAll(c));
            }
        }

        deleteJoinsForManyToMany();
        for (Model model : allMany2ManyChildren) {
            model.deleteCascade();
        }
    }

    /**
     * Deletes this record from associated table, as well as its immediate children. This is a high performance method
     * because it does not walk through a chain of child dependencies like {@link #deleteCascade()} does, but rather issues
     * one DELETE statement per child dependency table. Also, its semantics are a bit different between than {@link #deleteCascade()}.
     * It only deletes current record and immediate children, but not their children (no grand kinds are dead as a result :)).
     * <h4>One to many and polymorphic associations</h4>
     * The current record is deleted, as well as immediate children.
     * <h4>Many to many associations</h4>
     * The current record is deleted, as well as links in a join table. Nothing else is deleted.
     * <p/>
     * After deletion, this instance becomes {@link #frozen()} and cannot be used anymore until {@link #thaw()} is called.
     */
    public void deleteCascadeShallow(){
        deleteJoinsForManyToMany();
        deleteOne2ManyChildrenShallow();
        deletePolymorphicChildrenShallow();
        delete();
    }


    private void deleteJoinsForManyToMany() {
        List<? extends Association> associations = getMetaModelLocal().getManyToManyAssociations(Collections.<Association>emptyList());
        for (Association association : associations) {
            String join = ((Many2ManyAssociation)association).getJoin();
            String sourceFK = ((Many2ManyAssociation)association).getSourceFkName();
            String query = "DELETE FROM " + join + " WHERE " + sourceFK + " = " + getId();
            new DB(getMetaModelLocal().getDbName()).exec(query);
        }
    }

    private void deleteOne2ManyChildrenShallow() {
        List<OneToManyAssociation> childAssociations = getMetaModelLocal().getOneToManyAssociations(Collections.<Association>emptyList());
        for (OneToManyAssociation association : childAssociations) {
            String  target = association.getTarget();
            String query = "DELETE FROM " + target + " WHERE " + association.getFkName() + " = ?";
            new DB(getMetaModelLocal().getDbName()).exec(query, getId());
        }
    }

    private void deletePolymorphicChildrenShallow() {
        List<OneToManyPolymorphicAssociation> polymorphics = getMetaModelLocal().getPolymorphicAssociations(new ArrayList<Association>());
        for (OneToManyPolymorphicAssociation association : polymorphics) {
            String  target = association.getTarget();
            String parentType = association.getTypeLabel();
            String query = "DELETE FROM " + target + " WHERE parent_id = ? AND parent_type = ?";
            new DB(getMetaModelLocal().getDbName()).exec(query, getId(), parentType);
        }
    }


    private void deleteChildrenDeep(List<? extends Association> childAssociations){
        for (Association association : childAssociations) {
            String targetTableName = association.getTarget();
            Class c = Registry.instance().getModelClass(targetTableName, false);
            if(c == null){// this model is probably not defined as a class, but the table exists!
                logger.error("ActiveJDBC WARNING: failed to find a model class for: {}, maybe model is not defined for this table?"
                        + " There might be a risk of running into integrity constrain violation if this model is not defined.",
                        targetTableName);
            }
            else{
                List<Model> dependencies = getAll(c);
                for (Model model : dependencies) {
                    model.deleteCascade();
                }
            }
        }
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
    public static int delete(String query, Object... params) {
        return ModelDelegate.delete(modelClass(), query, params);
    }

    /**
     * Returns true if record corresponding to the id passed exists in the DB.
     *
     * @param id id in question.
     * @return true if corresponding record exists in DB, false if it does not.
     */
    public static boolean exists(Object id) {
        return ModelDelegate.exists(modelClass(), id);
    }

    /**
     * Returns true if record corresponding to the id of this instance exists in  the DB.
     *
     * @return true if corresponding record exists in DB, false if it does not.
     */
    public boolean exists(){
        MetaModel metaModel = getMetaModelLocal();
        return null != new DB(metaModel.getDbName()).firstCell(metaModel.getDialect().selectExists(metaModel), getId());
    }

    /**
     * Deletes all records from associated table. This methods does not take associations into account.
     *
     * @return number of records deleted.
     */
    public static int deleteAll() {
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
    public static int update(String updates, String conditions, Object ... params) {
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
    public static int updateAll(String updates, Object ... params) {
        return ModelDelegate.updateAll(modelClass(), updates, params);
    }

    /**
     * Returns all values of the model with all attribute names converted to lower case,
     * regardless how these names came from DB. This method is a convenience
     * method for displaying values on web pages.
     *
     * <p/>
     * If {@link LazyList#include(Class[])} method was used, and this
     * model belongs to a parent (as in many to one relationship), then the parent
     * will be eagerly loaded and also converted to a map. Parents' maps are keyed in the
     * returned map by underscored name of a parent model class name.
     * <p/>
     * For example, if this model were <code>Address</code>
     * and a parent is <code>User</code> (and user has many addresses), then the resulting map would
     * have all the attributes of the current table and another map representing a parent user with a
     * key "user" in current map.
     *
     * @return all values of the model with all attribute names converted to lower case.
     */
    public Map<String, Object> toMap(){
        Map<String, Object> retVal = new TreeMap<String, Object>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            Object v = entry.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Clob) {
                retVal.put(entry.getKey().toLowerCase(), Convert.toString(v));
            } else {
                retVal.put(entry.getKey().toLowerCase(), v);
            }
        }
        for(Class parentClass: cachedParents.keySet()){
            retVal.put(underscore(parentClass.getSimpleName()), cachedParents.get(parentClass).toMap());
        }

        for(Class childClass: cachedChildren.keySet()){
            List<Model> children = cachedChildren.get(childClass);

            List<Map> childMaps = new ArrayList<Map>(children.size());
            for(Model child:children){
                childMaps.add(child.toMap());
            }
            retVal.put(tableize(childClass.getSimpleName()), childMaps);
        }
        return retVal;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("Model: ").append(getClass().getName())
          .append(", table: '").append(getMetaModelLocal().getTableName())
          .append("', attributes: ").append(attributes);

        if (cachedParents.size() > 0) {
            sb.append(", parents: ").append(cachedParents);
        }

        if (cachedChildren.size() > 0) {
            sb.append(", children: ").append(cachedChildren);
        }
        return sb.toString();
    }


    /**
     * Parses XML into a model. It expects the same structure of XML as the method {@link #toXml(int, boolean, String...)}.
     * It ignores children and dependencies (for now) if any. This method  will parse the model attributes
     * from the XML document, and will then call {@link #fromMap(java.util.Map)} method. It does not save data into a database, just sets the
     * attributes.
     *
     * @param xml xml to read model attributes from.
     */
    public void fromXml(String xml) {

        try{
            //such dumb API!
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));
            String topTag = underscore(getClass().getSimpleName());
            Element root = document.getDocumentElement();

            if(!root.getTagName().equals(topTag)){
                throw new InitException("top node has to match model name: " + topTag);
            }
            NodeList childNodes = root.getChildNodes();

            Map<String, String> attributesMap = new HashMap<String, String>();
            for(int i = 0; i < childNodes.getLength();i++){
                Node node  = childNodes.item(i);
                if(node instanceof Element){
                    Element child = (Element) node;
                    attributesMap.put(child.getTagName(), child.getFirstChild().getNodeValue());//this is even dumber!
                }
            }
            fromMap(attributesMap);
        }catch(Exception e){
            throw  new InitException(e);
        }
    }

    /**
     * Generates a XML document from content of this model.
     *
     * @param pretty pretty format (human readable), or one line text.
     * @param declaration true to include XML declaration at the top
     * @param attributeNames list of attributes to include. No arguments == include all attributes.
     * @return generated XML.
     */
    public String toXml(boolean pretty, boolean declaration, String... attributeNames) {
        StringBuilder sb = new StringBuilder();

        if(declaration) {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            if (pretty) { sb.append('\n'); }
        }
        toXmlP(sb, pretty, "", attributeNames);
        return sb.toString();
    }

    protected void toXmlP(StringBuilder sb, boolean pretty, String indent, String... attributeNames) {

        String topTag = underscore(getClass().getSimpleName());
        if (pretty) { sb.append(indent); }
        sb.append('<').append(topTag).append('>');
        if (pretty) { sb.append('\n'); }

        String[] names = !empty(attributeNames) ? attributeNames : attributeNamesLowerCased();
        for (String name : names) {
            if (pretty) { sb.append("  ").append(indent); }
            sb.append('<').append(name).append('>');
            Object v = attributes.get(name);
            if (v != null) {
                Escape.xml(sb, Convert.toString(v));
            }
            sb.append("</").append(name).append('>');
            if (pretty) { sb.append('\n'); }
        }
        for (Class childClass : cachedChildren.keySet()) {
            if (pretty) { sb.append("  ").append(indent); }
            String tag = pluralize(underscore(childClass.getSimpleName()));
            sb.append('<').append(tag).append('>');
            if (pretty) { sb.append('\n'); }
            for (Model child : cachedChildren.get(childClass)) {
                child.toXmlP(sb, pretty, "    " + indent);
            }
            if (pretty) { sb.append("  ").append(indent); }
            sb.append("</").append(tag).append('>');
            if (pretty) { sb.append('\n'); }
        }
        beforeClosingTag(sb, pretty, pretty ? "  " + indent : "", attributeNames);
        if (pretty) { sb.append(indent); }
        sb.append("</").append(topTag).append('>');
        if (pretty) { sb.append('\n'); }
    }

    /**
     * Generates a XML document from content of this model.
     *
     * @param spaces by how many spaces to indent.
     * @param declaration true to include XML declaration at the top
     * @param attributeNames list of attributes to include. No arguments == include all attributes.
     * @return generated XML.
     *
     * @deprecated use {@link #toXml(boolean, boolean, String...)} instead
     */
    @Deprecated
    public String toXml(int spaces, boolean declaration, String... attributeNames) {
        return toXml(spaces > 0, declaration, attributeNames);
    }

    /**
     * Override in a subclass to inject custom content onto XML just before the closing tag.
     *
     * <p>To keep the formatting, it is recommended to implement this method as the example below.
     *
     * <blockquote><pre>
     * if (pretty) { sb.append(ident); }
     * sb.append("&lt;test&gt;...&lt;/test&gt;");
     * if (pretty) { sb.append('\n'); }
     * </pre></blockquote>
     *
     * @param sb to write content to.
     * @param pretty pretty format (human readable), or one line text.
     * @param indent indent at current level
     * @param attributeNames list of attributes to include
     */
    public void beforeClosingTag(StringBuilder sb, boolean pretty, String indent, String... attributeNames) {
        StringWriter writer = new StringWriter();
        beforeClosingTag(indent.length(), writer, attributeNames);
        sb.append(writer.toString());
    }

    /**
     * Override in a subclass to inject custom content onto XML just before the closing tag.
     *
     * @param spaces number of spaces of indent
     * @param writer to write content to.
     * @param attributeNames list of attributes to include
     *
     * @deprecated use {@link #beforeClosingTag(StringBuilder, boolean, String, String...)} instead
     */
    @Deprecated
    public void beforeClosingTag(int spaces, StringWriter writer, String... attributeNames) {
        // do nothing
    }

    /**
     * Generates a JSON document from content of this model.
     *
     * @param pretty pretty format (human readable), or one line text.
     * @param attributeNames  list of attributes to include. No arguments == include all attributes.
     * @return generated JSON.
     */
    public String toJson(boolean pretty, String... attributeNames) {
        StringBuilder sb = new StringBuilder();
        toJsonP(sb, pretty, "", attributeNames);
        return sb.toString();
    }

    protected void toJsonP(StringBuilder sb, boolean pretty, String indent, String... attributeNames) {
        if (pretty) { sb.append(indent); }
        sb.append('{');

        String[] names = !empty(attributeNames) ? attributeNames : attributeNamesLowerCased();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) { sb.append(','); }
            if (pretty) { sb.append("\n  ").append(indent); }
            String name = names[i];
            sb.append('"').append(name).append("\":");
            Object v = attributes.get(name);
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof Date) {
                sb.append('"').append(Convert.toIsoString((Date) v)).append('"');
            } else {
                sb.append('"');
                Escape.json(sb, Convert.toString(v));
                sb.append('"');
            }
        }

        if (cachedChildren.size() > 0) {

            sb.append(',');
            if (pretty) { sb.append("\n  ").append(indent); }
            sb.append("\"children\":{");

            List<Class> childClasses = new ArrayList<Class>();
            childClasses.addAll(cachedChildren.keySet());
            for (int i = 0; i < childClasses.size(); i++) {
                if (i > 0) { sb.append(','); }
                Class childClass = childClasses.get(i);
                String name = pluralize(childClass.getSimpleName()).toLowerCase();
                if (pretty) { sb.append("\n    ").append(indent); }
                sb.append('"').append(name).append("\":[");

                List<Model> child = cachedChildren.get(childClass);
                for (int j = 0; j < child.size(); j++) {
                    if (j > 0) { sb.append(','); }
                    if (pretty) { sb.append('\n'); }
                    child.get(j).toJsonP(sb, pretty, (pretty ? "      " + indent : ""));
                }

                if (pretty) { sb.append("\n    ").append(indent); }
                sb.append(']');
            }
            if (pretty) { sb.append("\n  ").append(indent); }
            sb.append('}');
        }

        beforeClosingBrace(sb, pretty, pretty ? "  " + indent : "", attributeNames);
        if (pretty) { sb.append('\n').append(indent); }
        sb.append('}');
    }

    /**
     * Override in subclasses in order to inject custom content into Json just before the closing brace.
     *
     * <p>To keep the formatting, it is recommended to implement this method as the example below.
     *
     * <blockquote><pre>
     * sb.append(',');
     * if (pretty) { sb.append('\n').append(indent); }
     * sb.append("\"test\":\"...\"");
     * </pre></blockquote>
     *
     * @param sb to write custom content to
     * @param pretty pretty format (human readable), or one line text.
     * @param indent indent at current level
     * @param attributeNames list of attributes to include
     */
    public void beforeClosingBrace(StringBuilder sb, boolean pretty, String indent, String... attributeNames) {
        StringWriter writer = new StringWriter();
        beforeClosingBrace(pretty, indent, writer);
        sb.append(writer.toString());
    }

    /**
     * Override in subclasses in order to inject custom content into Json just before the closing brace.
     *
     * @param pretty pretty format (human readable), or one line text.
     * @param indent indent at current level
     * @param writer writer to write custom content to
     * @deprecated use {@link #beforeClosingBrace(StringBuilder, boolean, String, String...)} instead
     */
    @Deprecated
    public void beforeClosingBrace(boolean pretty, String indent, StringWriter writer) {
        // do nothing
    }

    private String[] attributeNamesLowerCased() {
        return ModelDelegate.lowerCased(attributes.keySet());
    }

    /**
     * Returns parent of this model, assuming that this table represents a child.
     * This method may return <code>null</code> in cases when you have orphan record and
     * referential integrity is not enforced in DBMS with a foreign key constraint.
     *
     * @param parentClass   class of a parent model.
     * @return instance of a parent of this instance in the "belongs to"  relationship.
     */
    public <P extends Model> P parent(Class<P> parentClass) {
        return parent(parentClass, false);
    }

    public <P extends Model> P parent(Class<P> parentClass, boolean cache) {
        P cachedParent = parentClass.cast(cachedParents.get(parentClass));
        if (cachedParent != null) {
            return cachedParent;
        }
        MetaModel parentMM = Registry.instance().getMetaModel(parentClass);
        String parentTable = parentMM.getTableName();

        BelongsToAssociation ass = getMetaModelLocal().getAssociationForTarget(parentTable, BelongsToAssociation.class);
        BelongsToPolymorphicAssociation assP = getMetaModelLocal().getAssociationForTarget(
                parentTable, BelongsToPolymorphicAssociation.class);

        Object fkValue;
        String fkName;
        if (ass != null) {
            fkValue = get(ass.getFkName());
            fkName = ass.getFkName();
        } else if (assP != null) {
            fkValue = get("parent_id");
            fkName = "parent_id";

            if (!assP.getTypeLabel().equals(getString("parent_type"))) {
                throw new IllegalArgumentException("Wrong parent: '" + parentClass + "'. Actual parent type label of this record is: '" + getString("parent_type") + "'");
            }
        } else {
            throw new IllegalArgumentException("there is no association with table: " + parentTable);
        }

        if (fkValue == null) {
            logger.debug("Attribute: {} is null, cannot determine parent. Child record: {}", fkName, this);
            return null;
        }
        String parentIdName = parentMM.getIdName();
        String query = getMetaModelLocal().getDialect().selectStarParametrized(parentTable, parentIdName);

        if (parentMM.cached()) {
            P parent = parentClass.cast(QueryCache.instance().getItem(parentTable, query, new Object[]{fkValue}));
            if (parent != null) {
                return parent;
            }
        }

        List<Map> results = new DB(getMetaModelLocal().getDbName()).findAll(query, fkValue);
        //expect only one result here
        if (results.isEmpty()) { //this should be covered by referential integrity constraint
            return null;
        } else {
            try {
                P parent = parentClass.newInstance();
                parent.hydrate(results.get(0), true);
                if (parentMM.cached()) {
                    QueryCache.instance().addItem(parentTable, query, new Object[]{fkValue}, parent);
                }
                if (cache) {
                    setCachedParent(parent);
                }
                return parent;
            } catch (Exception e) {
                throw new InitException(e.getMessage(), e);
            }
        }
    }

    protected void setCachedParent(Model parent) {
        if (parent != null) {
            cachedParents.put(parent.getClass(), parent);
        }
    }


    /**
     * Sets multiple parents on this instance. Basically this sets a correct value of a foreign keys in a
     * parent/child relationship. This only works for one to many and polymorphic associations.
     *
     * @param parents - collection of potential parents of this instance. Its ID values must not be null.
     */
    public void setParents(Model... parents){
        for (Model parent : parents) {
            setParent(parent);
        }
    }


    /**
     * Sets a parent on this instance. Basically this sets a correct value of a foreign key in a
     * parent/child relationship. This only works for one to many and polymorphic associations.
     * The act of setting a parent does not result in saving to a database.
     *
     * @param parent potential parent of this instance. Its ID value must not be null.
     */
    public void setParent(Model parent) {
        if (parent == null || parent.getId() == null) {
            throw new IllegalArgumentException("parent cannot ne null and parent ID cannot be null");
        }
        List<Association> associations = getMetaModelLocal().getAssociations();
        for (Association association : associations) {
            if (association instanceof BelongsToAssociation && association.getTarget().equals(parent.getMetaModelLocal().getTableName())) {
                set(((BelongsToAssociation)association).getFkName(), parent.getId());
                return;
            }
            if(association instanceof BelongsToPolymorphicAssociation && association.getTarget().equals(parent.getMetaModelLocal().getTableName())){
                set("parent_id", parent.getId());
                set("parent_type", ((BelongsToPolymorphicAssociation)association).getTypeLabel());
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(parent.getClass()).append(" is not associated with ").append(this.getClass())
                .append(", list of existing associations:\n");
        join(sb, getMetaModelLocal().getAssociations(), "\n");
        throw new IllegalArgumentException(sb.toString());
    }

    /**
     * Copies all attribute values (except for ID, created_at and updated_at) from this instance to the other.
     *
     * @param other target model.
     */
    public void copyTo(Model other) {
        other.copyFrom(this);
    }

    /**
     * Copies all attribute values (except for ID, created_at and updated_at) from other instance to this one.
     *
     * @param other source model.
     */
    public void copyFrom(Model other) {
        if (!getMetaModelLocal().getTableName().equals(other.getMetaModelLocal().getTableName())) {
            throw new IllegalArgumentException("can only copy between the same types");
        }
        Map<String, Object> otherAttributes = other.getAttributes();
        for (String name : getMetaModelLocal().getAttributeNamesSkipId()) {
            attributes.put(name, otherAttributes.get(name));
            dirtyAttributeNames.add(name);
            // Why not use setRaw() here? Does the same and avoids duplication of code... (Garagoth)
            // other.setRaw(name, getRaw(name));
        }
    }

    /**
     * This method should be called from all instance methods for performance.
     *
     * @return
     */
    protected MetaModel getMetaModelLocal() {
        if (metaModelLocal == null) {
            // optimized not to depend on static or instrumented methods
            metaModelLocal = Registry.instance().getMetaModel(this.getClass());
        }
        return metaModelLocal;
    }


    protected void setMetamodelLocal(MetaModel metamodelLocal){
        this.metaModelLocal = metamodelLocal;
    }

    ModelRegistry modelRegistryLocal() {
        if (modelRegistryLocal == null) {
            // optimized not to depend on static or instrumented methods
            modelRegistryLocal = Registry.instance().modelRegistryOf(this.getClass());
        }
        return modelRegistryLocal;
    }

    /**
     * Re-reads all attribute values from DB.
     *
     */
    public void refresh() {
        Model fresh = ModelDelegate.findById(this.getClass(), getId());
        if (fresh == null) {
            throw new StaleModelException("Failed to refresh self because probably record with " +
                    "this ID does not exist anymore. Stale model: " + this);
        }
        fresh.copyTo(this);
        dirtyAttributeNames.clear();
    }

    /**
     * Returns a value for attribute.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Object</code>, given the attribute value is an instance of <code>S</code>, then it will be used.
     *
     * <h3>Infer relationship from name of argument</h3>
     * Besides returning direct attributes of this model, this method is also
     * aware of relationships and can return collections based on naming conventions. For example, if a model User has a
     * one to many relationship with a model Address, then the following code will work:
     * <pre>
     * Address address = ...;
     * User user = (User)address.get("user");
     * </pre>
     * Conversely, this will also work:
     * <pre>
     * List&lt;Address&gt; addresses = (List&lt;Address&gt;)user.get(&quot;addresses&quot;);
     * </pre>
     *
     * The same would also work for many to many relationships:
     * <pre>
     * List&lt;Doctor&gt; doctors = (List&lt;Doctor&gt;)patient.get(&quot;doctors&quot;);
     * ...
     * List&lt;Patient&gt; patients = (List&lt;Patient&gt;)doctor.get(&quot;patients&quot;);
     * </pre>
     *
     * This methods will try to infer a name if a table by using {@link org.javalite.common.Inflector} to try to
     * convert it to singular and them plural form, an attempting to see if this model has an appropriate relationship
     * with another model, if one is found. This method of finding of relationships is best used in templating
     * technologies, such as JSPs. For standard cases, please use {@link #parent(Class)}, and {@link #getAll(Class)}.
     *
     * <h3>Suppressing inference for performance</h3>
     * <p>
     *     In some cases, the inference of relationships might take a toll on performance, and if a project is not using
     *     the getter method for inference, than it is wise to turn it off with a system property <code>activejdbc.get.inference</code>:
     *
     *     <pre>
     *         -Dactivejdbc.get.inference = false
     *     </pre>
     *     If inference is turned off, only a value of the attribute is returned.
     * </p>
     *
     * @param attributeName name of attribute of name or related object.
     * @return value for attribute.
     */
    public Object get(String attributeName) {
        if (frozen) { throw new FrozenException(this); }

        if (attributeName == null) { throw new IllegalArgumentException("attributeName cannot be null"); }

        // NOTE: this is a workaround for JSP pages. JSTL in cases ${item.id} does not call the getId() method, instead
        // calls item.get("id"), considering that this is a map only!
        if (attributeName.equalsIgnoreCase("id") && !attributes.containsKey("id")) {
            return attributes.get(getIdName());
        }

        if (getMetaModelLocal().hasAttribute(attributeName)) {
            Object value = attributes.get(attributeName);
            Converter<Object, Object> converter = modelRegistryLocal().converterForValue(attributeName, value, Object.class);
            return converter != null ? converter.convert(value) : value;
        } else {
            String getInferenceProperty = System.getProperty("activejdbc.get.inference");
            if (getInferenceProperty == null || getInferenceProperty.equals("true")) {
                Object returnValue;
                if ((returnValue = tryParent(attributeName)) != null) {
                    return returnValue;
                } else if ((returnValue = tryPolymorphicParent(attributeName)) != null) {
                    return returnValue;
                } else if ((returnValue = tryChildren(attributeName)) != null) {
                    return returnValue;
                } else if ((returnValue = tryPolymorphicChildren(attributeName)) != null) {
                    return returnValue;
                } else if ((returnValue = tryOther(attributeName)) != null) {
                    return returnValue;
                } else {
                    getMetaModelLocal().checkAttributeOrAssociation(attributeName);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Gets raw value of the attribute, without conversions applied.
     */
    private Object getRaw(String attributeName) {
        if(frozen) throw new FrozenException(this);

        if(attributeName == null) throw new IllegalArgumentException("attributeName cannot be null");

        return attributes.get(attributeName);//this should account for nulls too!
    }


    private Object tryPolymorphicParent(String parentTable){
        MetaModel parentMM = inferTargetMetaModel(parentTable);
        if(parentMM == null){
            return null;
        }else
            return getMetaModelLocal().hasAssociation(parentMM.getTableName(), BelongsToPolymorphicAssociation.class) ?
                parent(parentMM.getModelClass()): null;
    }

    private Object tryParent(String parentTable){
        MetaModel parentMM = inferTargetMetaModel(parentTable);
        if(parentMM == null){
            return null;
        }else
            return getMetaModelLocal().hasAssociation(parentMM.getTableName(), BelongsToAssociation.class) ?
                parent(parentMM.getModelClass()): null;
    }

    private Object tryPolymorphicChildren(String childTable){
        MetaModel childMM = inferTargetMetaModel(childTable);
        if(childMM == null){
            return null;
        }else
            return getMetaModelLocal().hasAssociation(childMM.getTableName(), OneToManyPolymorphicAssociation.class) ?
                getAll(childMM.getModelClass()): null;
    }

    private Object tryChildren(String childTable){
        MetaModel childMM = inferTargetMetaModel(childTable);
        if(childMM == null){
            return null;
        }else
            return getMetaModelLocal().hasAssociation(childMM.getTableName(), OneToManyAssociation.class) ?
                getAll(childMM.getModelClass()): null;
    }

    private Object tryOther(String otherTable){
        MetaModel otherMM = inferTargetMetaModel(otherTable);
        if(otherMM == null){
            return null;
        }else
            return getMetaModelLocal().hasAssociation(otherMM.getTableName(), Many2ManyAssociation.class) ?
                getAll(otherMM.getModelClass()): null;
    }

    private MetaModel inferTargetMetaModel(String targetTableName){
        String targetTable = singularize(targetTableName);
        MetaModel targetMM = Registry.instance().getMetaModel(targetTable);
        if(targetMM == null){
            targetTable = pluralize(targetTableName);
            targetMM = Registry.instance().getMetaModel(targetTable);
        }
        return targetMM != null? targetMM: null;
    }

    /*************************** typed getters *****************************************/
    /**
     * Gets attribute value as <code>String</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.String</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toString(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>String</code>
     */
    public String getString(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, String> converter = modelRegistryLocal().converterForValue(attributeName, value, String.class);
        return converter != null ? converter.convert(value) : Convert.toString(value);
    }

    /**
     * Gets a value as bytes. If the column is Blob, bytes are
     * read directly, if not, then the value is converted to String first, then
     * string bytes are returned. Be careful out there,  this will read entire
     * Blob onto memory.
     *
     * @param attributeName name of attribute
     * @return value as bytes.
     */
    //TODO: use converters here?
    public byte[] getBytes(String attributeName) {
        Object value = get(attributeName);
        return Convert.toBytes(value);
    }

    /**
     * Gets attribute value as <code>java.math.BigDecimal</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.math.BigDecimal</code>, given the attribute value is an instance of <code>S</code>, then it will be
     * used, otherwise performs a conversion using {@link Convert#toBigDecimal(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>java.math.BigDecimal</code>
     */
    public BigDecimal getBigDecimal(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, BigDecimal> converter = modelRegistryLocal().converterForValue(
                attributeName, value, BigDecimal.class);
        return converter != null ? converter.convert(value) : Convert.toBigDecimal(value);
    }

    /**
     * Gets attribute value as <code>Integer</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Integer</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toInteger(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>Integer</code>
     */
    public Integer getInteger(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Integer> converter = modelRegistryLocal().converterForValue(attributeName, value, Integer.class);
        return converter != null ? converter.convert(value) : Convert.toInteger(value);
    }

    /**
     * Gets attribute value as <code>Long</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Long</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toLong(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>Long</code>
     */
    public Long getLong(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Long> converter = modelRegistryLocal().converterForValue(attributeName, value, Long.class);
        return converter != null ? converter.convert(value) : Convert.toLong(value);
    }

    /**
     * Gets attribute value as <code>Short</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Short</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toShort(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>Short</code>
     */
    public Short getShort(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Short> converter = modelRegistryLocal().converterForValue(attributeName, value, Short.class);
        return converter != null ? converter.convert(value) : Convert.toShort(value);
    }

    /**
     * Gets attribute value as <code>Float</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Float</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toFloat(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>Float</code>
     */
    public Float getFloat(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Float> converter = modelRegistryLocal().converterForValue(attributeName, value, Float.class);
        return converter != null ? converter.convert(value) : Convert.toFloat(value);
    }

    /**
     * Gets attribute value as <code>java.sql.Time</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.sql.Time</code>, given the attribute value is an instance of <code>S</code>, then it will be
     * used, otherwise performs a conversion using {@link Convert#toTime(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return instance of <code>Timestamp</code>
     */
    public Time getTime(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Time> converter = modelRegistryLocal().converterForValue(
                attributeName, value, Time.class);
        return converter != null ? converter.convert(value) : Convert.toTime(value);
    }

    /**
     * Gets attribute value as <code>java.sql.Timestamp</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.sql.Timestamp</code>, given the attribute value is an instance of <code>S</code>, then it will be
     * used, otherwise performs a conversion using {@link Convert#toTimestamp(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return instance of <code>Timestamp</code>
     */
    public Timestamp getTimestamp(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Timestamp> converter = modelRegistryLocal().converterForValue(
                attributeName, value, Timestamp.class);
        return converter != null ? converter.convert(value) : Convert.toTimestamp(value);
    }

    /**
     * Gets attribute value as <code>Double</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Double</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toDouble(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>Double</code>
     */
    public Double getDouble(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Double> converter = modelRegistryLocal().converterForValue(attributeName, value, Double.class);
        return converter != null ? converter.convert(value) : Convert.toDouble(value);
    }

    /**
     * Gets attribute value as <code>Boolean</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Boolean</code>, given the attribute value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toBoolean(Object)}.
     *
     * @param attributeName name of attribute to convert
     * @return value converted to <code>Boolean</code>
     */
    public Boolean getBoolean(String attributeName) {
        Object value = getRaw(attributeName);
        Converter<Object, Boolean> converter = modelRegistryLocal().converterForValue(attributeName, value, Boolean.class);
        return converter != null ? converter.convert(value) : Convert.toBoolean(value);
    }

    /*************************** typed setters *****************************************/

    /**
     * Sets attribute value as <code>String</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.String</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toString(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setString(String attributeName, Object value) {
        Converter<Object, String> converter = modelRegistryLocal().converterForValue(attributeName, value, String.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toString(value));
    }

    /**
     * Sets attribute value as <code>java.math.BigDecimal</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.math.BigDecimal</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toBigDecimal(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setBigDecimal(String attributeName, Object value) {
        Converter<Object, BigDecimal> converter = modelRegistryLocal().converterForValue(
                attributeName, value, BigDecimal.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toBigDecimal(value));
    }

    /**
     * Sets attribute value as <code>Short</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Short</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toShort(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setShort(String attributeName, Object value) {
        Converter<Object, Short> converter = modelRegistryLocal().converterForValue(attributeName, value, Short.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toShort(value));
    }

    /**
     * Sets attribute value as <code>Integer</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Integer</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toInteger(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setInteger(String attributeName, Object value) {
        Converter<Object, Integer> converter = modelRegistryLocal().converterForValue(attributeName, value, Integer.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toInteger(value));
    }

    /**
     * Sets attribute value as <code>Long</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Long</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toLong(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setLong(String attributeName, Object value) {
        Converter<Object, Long> converter = modelRegistryLocal().converterForValue(attributeName, value, Long.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toLong(value));
    }

    /**
     * Sets attribute value as <code>Float</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Float</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toFloat(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value to convert.
     * @return reference to this model.
     */
    public <T extends Model> T setFloat(String attributeName, Object value) {
        Converter<Object, Float> converter = modelRegistryLocal().converterForValue(attributeName, value, Float.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toFloat(value));
    }

    /**
     * Sets attribute value as <code>java.sql.Time</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.sql.Time</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toTime(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setTime(String attributeName, Object value) {
        Converter<Object, Time> converter = modelRegistryLocal().converterForValue(
                attributeName, value, Time.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toTime(value));
    }

    /**
     * Sets attribute value as <code>java.sql.Timestamp</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.sql.Timestamp</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toTimestamp(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setTimestamp(String attributeName, Object value) {
        Converter<Object, Timestamp> converter = modelRegistryLocal().converterForValue(
                attributeName, value, Timestamp.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toTimestamp(value));
    }

    /**
     * Sets attribute value as <code>Double</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Double</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toDouble(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value to convert.
     * @return reference to this model.
     */
    public <T extends Model> T setDouble(String attributeName, Object value) {
        Converter<Object, Double> converter = modelRegistryLocal().converterForValue(attributeName, value, Double.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toDouble(value));
    }

    /**
     * Sets attribute value as <code>Boolean</code>.
     * If there is a {@link Converter} registered for the attribute that converts from Class <code>S</code> to Class
     * <code>java.lang.Boolean</code>, given the value is an instance of <code>S</code>, then it will be used,
     * otherwise performs a conversion using {@link Convert#toBoolean(Object)}.
     *
     * @param attributeName name of attribute.
     * @param value value
     * @return reference to this model.
     */
    public <T extends Model> T setBoolean(String attributeName, Object value) {
        Converter<Object, Boolean> converter = modelRegistryLocal().converterForValue(attributeName, value, Boolean.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toBoolean(value));
    }

    /**
     * This methods supports one to many, many to many relationships as well as polymorphic associations.
     * <p/>
     * In case of one to many, the <code>clazz</code>  must be a class of a child model, and it will return a
     * collection of all children.
     * <p/>
     * In case of many to many, the <code>clazz</code>  must be a class of a another related model, and it will return a
     * collection of all related models.
     * <p/>
     * In case of polymorphic, the <code>clazz</code>  must be a class of a polymorphically related model, and it will return a
     * collection of all related models.
     *
     *
     * @param clazz class of a child model for one to many, or class of another model, in case of many to many or class of child in case of
     * polymorphic
     *
     * @return list of children in case of one to many, or list of other models, in case many to many.
     */

    public <C extends Model> LazyList<C> getAll(Class<C> clazz) {
        List<Model> children = cachedChildren.get(clazz);
        if(children != null){
            return (LazyList<C>) children;
        }

        String tableName = Registry.instance().getTableName(clazz);
        if(tableName == null) throw new IllegalArgumentException("table: " + tableName + " does not exist for model: " + clazz);

        return get(tableName, null);
    }

    /**
     * This methods supports one to many, many to many relationships as well as polymorphic associations.
     * <p/>
     * In case of one to many, the <code>className</code>  must be a class of a child model, and it will return a
     * collection of all children.
     * <p/>
     * In case of many to many, the <code>className</code>  must be a class of a another related model, and it will return a
     * collection of all related models.
     * <p/>
     * In case of polymorphic, the <code>className</code>  must be a class of a polymorphically related model, and it will return a
     * collection of all related models.
     *
     *
     * @param className class of a child model for one to many, or class of another model, in case of many to many or class of child in case of
     * polymorphic
     *
     * @return list of children in case of one to many, or list of other models, in case many to many.
     * @throws ClassNotFoundException 
     */

    public <C extends Model> LazyList<C> getAll(String className) throws ClassNotFoundException {
    	return getAll((Class<C>) Class.forName(className));
    }

    /**
     * Provides a list of child models in one to many, many to many and polymorphic associations, but in addition also allows to filter this list
     * by criteria.
     *
     * <p/>
     * <strong>1.</strong> For one to many, the criteria is against the child table.
     *
     * <p/>
     * <strong>2.</strong> For polymorphic association, the criteria is against the child table.
     *
     * <p/>
     * <strong>3.</strong> For many to many, the criteria is against the join table.
     * For example, if you have table PROJECTS, ASSIGNMENTS and PROGRAMMERS, where a project has many programmers and a programmer
     * has many projects, and ASSIGNMENTS is a join table, you can write code like this, assuming that the ASSIGNMENTS table
     * has a column <code>duration_weeks</code>:
     *
     * <pre>
     * List<Project> threeWeekProjects = programmer.get(Project.class, "duration_weeks = ?", 3);
     * </pre>
     * where this list will contain all projects to which this programmer is assigned for 3 weeks.
     *
     * @param clazz related type
     * @param query sub-query for join table.
     * @param params parameters for a sub-query
     * @return list of relations in many to many
     */
    public <C extends Model> LazyList<C> get(Class<C> clazz, String query, Object ... params){
        return get(Registry.instance().getTableName(clazz), query, params);
    }

    private <C extends Model> LazyList<C> get(String targetTable, String criteria, Object ...params) {
        //TODO: interesting thought: is it possible to have two associations of the same name, one to many and many to many? For now, say no.

        OneToManyAssociation oneToManyAssociation = getMetaModelLocal().getAssociationForTarget(
                targetTable, OneToManyAssociation.class);
        Many2ManyAssociation manyToManyAssociation = getMetaModelLocal().getAssociationForTarget(
                targetTable, Many2ManyAssociation.class);
        OneToManyPolymorphicAssociation oneToManyPolymorphicAssociation = getMetaModelLocal().getAssociationForTarget(
                targetTable, OneToManyPolymorphicAssociation.class);

        String additionalCriteria =  criteria != null? " AND ( " + criteria + " ) " : "";
        String subQuery;
        if (oneToManyAssociation != null) {
            subQuery = oneToManyAssociation.getFkName() + " = ? " + additionalCriteria;
        } else if (manyToManyAssociation != null) {
            String targetId = Registry.instance().getMetaModel(targetTable).getIdName();
            String joinTable = manyToManyAssociation.getJoin();

            String query = "SELECT " + targetTable + ".* FROM " + targetTable + ", " + joinTable +
                " WHERE " + targetTable + "." + targetId + " = " + joinTable + "." + manyToManyAssociation.getTargetFkName() +
                " AND " + joinTable + "." + manyToManyAssociation.getSourceFkName() + " = ? " + additionalCriteria;

            Object[] allParams = new Object[params.length + 1];
            allParams[0] = getId();
            System.arraycopy(params, 0, allParams, 1, params.length);
            return new LazyList<C>(true, Registry.instance().getMetaModel(targetTable), query, allParams);
        } else if (oneToManyPolymorphicAssociation != null) {
            subQuery = "parent_id = ? AND " + " parent_type = '" + oneToManyPolymorphicAssociation.getTypeLabel() + "'" + additionalCriteria;
        } else {
            throw new NotAssociatedException(getMetaModelLocal().getTableName(), targetTable);
        }

        Object[] allParams = new Object[params.length + 1];
        allParams[0] = getId();
        System.arraycopy(params, 0, allParams, 1, params.length);
        return new LazyList<C>(subQuery, Registry.instance().getMetaModel(targetTable), allParams);
    }

    protected static NumericValidationBuilder validateNumericalityOf(String... attributeNames) {
        return ModelDelegate.validateNumericalityOf(modelClass(), attributeNames);
    }

    /**
     * Adds a validator to the model.
     *
     * @param validator new validator.
     */
    public static ValidationBuilder addValidator(Validator validator) {
        return ModelDelegate.validateWith(modelClass(), validator);
    }

    /**
     * Adds a new error to the collection of errors. This is a convenience method to be used from custom validators.
     *
     * @param key - key wy which this error can be retrieved from a collection of errors: {@link #errors()}.
     * @param value - this is a key of the message in the resource bundle.
     * @see {@link Messages}.
     */
    public void addError(String key, String value){
        errors.put(key, value);
    }

    public static void removeValidator(Validator validator){
        ModelDelegate.removeValidator(modelClass(), validator);
    }

    //TODO: missing no-arg getValidators()?
    public static List<Validator> getValidators(Class<? extends Model> clazz) {
        return ModelDelegate.validatorsOf(clazz);
    }

    /**
     * Validates an attribite format with a ree hand regular expression.
     *
     * @param attributeName attribute to validate.
     * @param pattern regexp pattern which must match  the value.
     * @return
     */
    protected static ValidationBuilder validateRegexpOf(String attributeName, String pattern) {
        return ModelDelegate.validateRegexpOf(modelClass(), attributeName, pattern);
    }

    /**
     * Validates email format.
     *
     * @param attributeName name of attribute that holds email value.
     * @return
     */
    protected static ValidationBuilder validateEmailOf(String attributeName) {
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
    protected static ValidationBuilder validateRange(String attributeName, Number min, Number max) {
        return ModelDelegate.validateRange(modelClass(), attributeName, min, max);
    }

    /**
     * The validation will not pass if the value is either an empty string "", or null.
     *
     * @param attributeNames list of attributes to validate.
     * @return
     */
    protected static ValidationBuilder validatePresenceOf(String... attributeNames) {
        return ModelDelegate.validatePresenceOf(modelClass(), attributeNames);
    }

    /**
     * Add a custom validator to the model.
     *
     * @param validator  custom validator.
     */
    protected static ValidationBuilder validateWith(Validator validator) {
        return ModelDelegate.validateWith(modelClass(), validator);
    }

    /**
     * Adds a custom converter to the model.
     *
     * @param converter custom converter
     * @deprecated use {@link #convertWith(org.javalite.activejdbc.conversion.Converter, String...)} instead
     */
    @Deprecated
    protected static ValidationBuilder convertWith(org.javalite.activejdbc.validation.Converter converter) {
        return ModelDelegate.convertWith(modelClass(), converter);
    }

    /**
     * Registers a custom converter for the specified attributes.
     *
     * @param converter custom converter
     * @param attributeNames attribute names
     */
    protected static void convertWith(Converter converter, String... attributeNames) {
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
    protected static ValidationBuilder convertDate(String attributeName, String format){
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
    protected static ValidationBuilder convertTimestamp(String attributeName, String format){
        return ModelDelegate.convertTimestamp(modelClass(), attributeName, format);
    }

    /**
     * Registers date format for specified attributes. This format will be used to convert between
     * Date -> String -> java.sql.Date when using the appropriate getters and setters.
     *
     * <p>For example:
     * <blockquote><pre>
     * public class Person extends Model {
     *     static {
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
    protected static void dateFormat(String pattern, String... attributeNames) {
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
    protected static void dateFormat(DateFormat format, String... attributeNames) {
        ModelDelegate.dateFormat(modelClass(), format, attributeNames);
    }

    /**
     * Registers date format for specified attributes. This format will be used to convert between
     * Date -> String -> java.sql.Timestamp when using the appropriate getters and setters.
     *
     * <p>For example:
     * <blockquote><pre>
     * public class Person extends Model {
     *     static {
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
    protected static void timestampFormat(String pattern, String... attributeNames) {
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
    protected static void timestampFormat(DateFormat format, String... attributeNames) {
        ModelDelegate.timestampFormat(modelClass(), format, attributeNames);
    }

    /**
     * Registers {@link BlankToNullConverter} for specified attributes. This will convert instances of <tt>String</tt>
     * that are empty or contain only whitespaces to <tt>null</tt>.
     *
     * @param attributeNames attribute names
     */
    protected static void blankToNull(String... attributeNames) {
        ModelDelegate.blankToNull(modelClass(), attributeNames);
    }

    /**
     * Registers {@link ZeroToNullConverter} for specified attributes. This will convert instances of <tt>Number</tt>
     * that are zero to <tt>null</tt>.
     *
     * @param attributeNames attribute names
     */
    protected static void zeroToNull(String... attributeNames) {
        ModelDelegate.zeroToNull(modelClass(), attributeNames);
    }

    public static boolean belongsTo(Class<? extends Model> targetClass) {
        return ModelDelegate.belongsTo(modelClass(), targetClass);
    }

    /**
     * @deprecated use {@link #callbackWith(CallbackListener...)} instead
     */
    @Deprecated
    public static void addCallbacks(CallbackListener... listeners) {
         ModelDelegate.callbackWith(modelClass(), listeners);
    }

    public static void callbackWith(CallbackListener... listeners) {
         ModelDelegate.callbackWith(modelClass(), listeners);
    }

    /**
     * This method performs validations and then returns true if no errors were generated, otherwise returns false.
     *
     * @return true if no errors were generated, otherwise returns false.
     */
    public boolean isValid(){
        validate();
        return !hasErrors();
    }

    /**
     * Executes all validators attached to this model.
     */
    public void validate() {
        fireBeforeValidation();
        errors = new Errors();
        List<Validator> validators = modelRegistryLocal().validators();
        if (validators != null) {
            for (Validator validator : validators) {
                validator.validate(this);
            }
        }
        fireAfterValidation();
    }

    public boolean hasErrors() {
        return errors != null && errors.size() > 0;
    }

    /**
     * Binds a validator to an attribute if validation fails.
     *
     * @param errorKey key of error in errors map. Usually this is a name of attribute,
     * but not limited to that, can be anything.
     *
     * @param validator -validator that failed validation.
     */
    public void addValidator(Validator validator, String errorKey) {
        if(!errors.containsKey(errorKey))
            errors.addValidator(errorKey, validator);
    }

    /**
     * Provides an instance of <code>Errors</code> object, filled with error messages after validation.
     *
     * @return an instance of <code>Errors</code> object, filled with error messages after validation.
     */
    public Errors errors() {
        return errors;
    }

    /**
     * Provides an instance of localized <code>Errors</code> object, filled with error messages after validation.
     *
     * @param locale locale.
     * @return an instance of localized <code>Errors</code> object, filled with error messages after validation.
     */
    public Errors errors(Locale locale) {
        errors.setLocale(locale);
        return errors;
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
    public static <T extends Model> T create(Object... namesAndValues) {
        return ModelDelegate.create(Model.<T>modelClass(), namesAndValues);
    }

    /**
     * This is a convenience method to set multiple values to a model.
     * Example:
     * <pre>
     * Person p = ...
     * Person p = p.set("name", "Sam", "last_name", "Margulis", "dob", "2001-01-07");
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
    public <T extends Model> T set(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("number of arguments must be even");
        }
        for (int i = 0; i < namesAndValues.length; ) {
            if (namesAndValues[i] == null) {
                throw new IllegalArgumentException("attribute names cannot be null");
            }
            set(namesAndValues[i++].toString(), namesAndValues[i++]);
        }
        return (T) this;
    }

    /**
     * This is a convenience method to {@link #create(Object...)}. It will create a new model and will save it
     * to DB. It has the same semantics as {@link #saveIt()}.
     *
     * @param namesAndValues names and values. elements at indexes 0, 2, 4, 8... are attribute names, and elements at
     * indexes 1, 3, 5... are values. Element at index 1 is a value for attribute at index 0 and so on.
     * @return newly instantiated model which also has been saved to DB.
     */
    public static <T extends Model> T createIt(Object ... namesAndValues){
        return ModelDelegate.createIt(Model.<T>modelClass(), namesAndValues);
    }

    public static <T extends Model> T findById(Object id) {
        return ModelDelegate.findById(Model.<T>modelClass(), id);
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
    public static <T extends Model> LazyList<T> where(String subquery, Object... params) {
        return ModelDelegate.where(Model.<T>modelClass(), subquery, params);
    }

    /**
     * Synonym of {@link #where(String, Object...)}
     *
     * @param subquery this is a set of conditions that normally follow the "where" clause. Example:
     * <code>"department = ? and dob &gt ?"</code>. If this value is "*" and no parameters provided, then {@link #findAll()} is executed.
     * @param params list of parameters corresponding to the place holders in the subquery.
     * @return instance of <code>LazyList<Model></code> containing results.
     */
    public static <T extends Model> LazyList<T> find(String subquery, Object... params) {
        return ModelDelegate.where(Model.<T>modelClass(), subquery, params);
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
    public static <T extends Model> T findFirst(String subQuery, Object... params) {
        return ModelDelegate.findFirst(Model.<T>modelClass(), subQuery, params);
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
    public static <T extends Model> T first(String subQuery, Object... params) {
        return ModelDelegate.findFirst(Model.<T>modelClass(), subQuery, params);
    }

    /**
     * This method is for processing really large result sets. Results found by this method are never cached.
     *
     * @param query query text.
     * @param listener this is a call back implementation which will receive instances of models found.
     * @deprecated use {@link #findWith(ModelListener, String, Object...)}.
     */
    @Deprecated
    public static void find(String query, final ModelListener listener) {
        ModelDelegate.findWith(modelClass(), listener, query);
    }

    /**
     * This method is for processing really large result sets. Results found by this method are never cached.
     *
     * @param listener this is a call back implementation which will receive instances of models found.
     * @param query sub-query (content after "WHERE" clause)
     * @param params optional parameters for a query.
     */
    public static void findWith(final ModelListener listener, String query, Object ... params) {
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
    public static <T extends Model> LazyList<T> findBySQL(String fullQuery, Object... params) {
        return ModelDelegate.findBySql(Model.<T>modelClass(), fullQuery, params);
    }

    /**
     * This method returns all records from this table. If you need to get a subset, look for variations of "find()".
     *
     * @return result list
     */
    public static <T extends Model> LazyList<T> findAll() {
        return ModelDelegate.findAll(Model.<T>modelClass());
    }

    /**
     * Adds a new child dependency. This method works for all three association types:
     * <ul>
     * <li>One to many - argument model should be a child in the relationship. This method will immediately set it's
     * ID as a foreign key on the child and will then save the child.</li>
     * <li>Many to many - argument model should be the other model in the relationship. This method will check if the
     * added child already has an ID. If the child does have an ID, then the method will create a link in the join
     * table. If the child does not have an ID, then this method saves the child first, then creates a record in the
     * join table linking this model instance and the child instance.</li>
     * <li>Polymorphic - argument model should be  a polymorphic child of this model. This method will set the
     * <code>parent_id</code> and <code>parent_type</code> as appropriate and then will then save the child.</li>
     * </ul>
     *
     * This method will throw a {@link NotAssociatedException} in case a model that has no relationship is passed.
     *
     * @param child instance of a model that has a relationship to the current model.
     * Either one to many or many to many relationships are accepted.
     */
    public void add(Model child) {

        if(child == null) throw new IllegalArgumentException("cannot add what is null");

        //TODO: refactor this method
        String childTable = Registry.instance().getTableName(child.getClass());
        MetaModel metaModel = getMetaModelLocal();
        if (getId() != null) {

            if (metaModel.hasAssociation(childTable, OneToManyAssociation.class)) {
                OneToManyAssociation ass = metaModel.getAssociationForTarget(childTable, OneToManyAssociation.class);
                String fkName = ass.getFkName();
                child.set(fkName, getId());
                child.saveIt();//this will cause an exception in case validations fail.
            }else if(metaModel.hasAssociation(childTable, Many2ManyAssociation.class)){
                Many2ManyAssociation ass = metaModel.getAssociationForTarget(childTable, Many2ManyAssociation.class);
                if (child.getId() == null) {
                    child.saveIt();
                }
                MetaModel joinMetaModel = Registry.instance().getMetaModel(ass.getJoin());
                if (joinMetaModel == null) {
                    new DB(metaModel.getDbName()).exec(metaModel.getDialect().insertManyToManyAssociation(ass),
                            getId(), child.getId());
                } else {
                    //TODO: write a test to cover this case:
                    //this is for Oracle, many 2 many, and all annotations used, including @IdGenerator. In this case,
                    //it is best to delegate generation of insert to a model (sequences, etc.)
                    try {
                        Model joinModel = joinMetaModel.getModelClass().newInstance();
                        joinModel.set(ass.getSourceFkName(), getId());
                        joinModel.set(ass.getTargetFkName(), child.getId());
                        joinModel.saveIt();
                    } catch (InstantiationException e) {
                        throw new InitException("failed to create a new instance of class: " + joinMetaModel.getClass()
                                + ", are you sure this class has a default constructor?", e);
                    } catch (IllegalAccessException e) {
                        throw new InitException(e);
                    } finally {
                        QueryCache.instance().purgeTableCache(ass.getJoin());
                        QueryCache.instance().purgeTableCache(metaModel.getTableName());
                        QueryCache.instance().purgeTableCache(childTable);
                    }
                }
             } else if(metaModel.hasAssociation(childTable, OneToManyPolymorphicAssociation.class)) {
                OneToManyPolymorphicAssociation ass = metaModel.getAssociationForTarget(
                        childTable, OneToManyPolymorphicAssociation.class);
                child.set("parent_id", getId());
                child.set("parent_type", ass.getTypeLabel());
                child.saveIt();

            }else
                throw new NotAssociatedException(metaModel.getTableName(), childTable);
        } else {
            throw new IllegalArgumentException("You can only add associated model to an instance that exists in DB. Save this instance first, then you will be able to add dependencies to it.");
        }
    }


    /**
     * Removes associated child from this instance. The child model should be either in belongs to association (including polymorphic) to this model
     * or many to many association.
     *
     * <h3>One to many and polymorphic associations</h3>
     * This method will simply call <code>child.delete()</code> method. This will render the child object frozen.
     *
     * <h3>Many to many associations</h3>
     * This method will remove an associated record from the join table, and will do nothing to the child model or record.
     *
     * <p/>
     * This method will throw a {@link NotAssociatedException} in case a model that has no relationship is passed.
     *
     * @param child model representing a "child" as in one to many or many to many association with this model.
     * @return number of records affected
     */
    public int remove(Model child) {
        if (child == null) { throw new IllegalArgumentException("cannot remove what is null"); }
        if (child.frozen() || child.getId() == null) {
            throw new IllegalArgumentException(
                    "Cannot remove a child that does not exist in DB (either frozen, or ID not set)");
        }
        if (getId() != null) {
            String childTable = Registry.instance().getTableName(child.getClass());
            MetaModel metaModel = getMetaModelLocal();
            if (metaModel.hasAssociation(childTable, OneToManyAssociation.class)
                    || metaModel.hasAssociation(childTable, OneToManyPolymorphicAssociation.class)) {
                child.delete();
                return 1;
            } else if (metaModel.hasAssociation(childTable, Many2ManyAssociation.class)) {
                return new DB(metaModel.getDbName()).exec(metaModel.getDialect().deleteManyToManyAssociation(
                        metaModel.getAssociationForTarget(childTable, Many2ManyAssociation.class)),
                        getId(), child.getId());
            } else {
                throw new NotAssociatedException(metaModel.getTableName(), childTable);
            }
        } else {
            throw new IllegalArgumentException("You can only add associated model to an instance that exists in DB. "
                    + "Save this instance first, then you will be able to add dependencies to it.");
        }
    }

    /**
     * This method will not exit silently like {@link #save()}, it instead will throw {@link org.javalite.activejdbc.validation.ValidationException}
     * if validations did not pass.
     *
     * @return  true if the model was saved, false if you set an ID value for the model, but such ID does not exist in DB.
     */
    public boolean saveIt() {
        boolean result = save();
        ModelDelegate.purgeEdges(getMetaModelLocal());
        if (!errors.isEmpty()) {
            throw new ValidationException(this);
        }
        return result;
    }



    /**
     * Resets all data in this model, including the ID.
     * After this method, this instance is equivalent to an empty, just created instance.
     */
    public void reset() {
        attributes = new CaseInsensitiveMap<Object>();
    }

    /**
     * Unfreezes this model. After this method it is possible again to call save() and saveIt() methods.
     * This method will erase the value of ID on this instance, while preserving all other attributes' values.
     *
     * If a record was deleted, it is frozen and cannot be saved. After it is thawed, it can be saved again, but it will
     * generate a new insert statement and create a new record in the table with all the same attribute values.
     *
     * <p/><p/>
     * Synonym for {@link #defrost()}.
     */
    public void thaw(){
        attributes.put(getIdName(), null);
        dirtyAttributeNames.addAll(attributes.keySet());
        frozen = false;
    }

    /**
     * Synonym for {@link #thaw()}.
     */
    public void defrost(){
        thaw();
    }

    /**
     * This method will save data from this instance to a corresponding table in the DB.
     * It will generate insert SQL if the model is new, or update if the model exists in the DB.
     * This method will execute all associated validations and if those validations generate errors,
     * these errors are attached to this instance. Errors are available by {#link #errors() } method.
     * The <code>save()</code> method is mostly for web applications, where code like this is written:
     * <pre>
     * if(person.save())
     *      //show page success
     * else{
     *      request.setAttribute("errors", person.errors());
     *      //show errors page, or same page so that user can correct errors.
     *   }
     * </pre>
     *
     * In other words, this method will not throw validation exceptions. However, if there is a problem in the DB, then
     * there can be a runtime exception thrown.
     *
     * @return true if a model was saved and false if values did not pass validations and the record was not saved.
     * False will also be returned if you set an ID value for the model, but such ID does not exist in DB.
     */
    public boolean save() {
        if(frozen) throw new FrozenException(this);

        fireBeforeSave();

        validate();
        if (hasErrors()) {
            return false;
        }

        boolean result;
        if (getId() == null) {
            result = insert();
        } else {
            result = update();
        }
        fireAfterSave();
        return result;
    }

    /**
     * Returns total count of records in table.
     *
     * @return total count of records in table.
     */
    public static Long count() {
        return ModelDelegate.count(modelClass());
    }

    /**
     * Returns count of records in table under a condition.
     *
     * @param query query to select records to count.
     * @param params parameters (if any) for the query.
     * @return count of records in table under a condition.
     */
    public static Long count(String query, Object... params) {
        return ModelDelegate.count(modelClass(), query, params);
    }

    /**
     * This method will save a model as new. In other words, it will not try to guess if this is a
     * new record or a one that exists in the table. It does not have "belt and suspenders", it will
     * simply generate and execute insert statement, assuming that developer knows what he/she is doing.
     *
     * @return true if model was saved, false if not
     */
    public boolean insert() {

        fireBeforeCreate();
        //TODO: fix this as created_at and updated_at attributes will be set even if insertion failed
        doCreatedAt();
        doUpdatedAt();

        MetaModel metaModel = getMetaModelLocal();
        List<String> columns = new ArrayList<String>();
        List<Object> values = new ArrayList<Object>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() == null || metaModel.getVersionColumn().equals(entry.getKey())) {
                continue;
            }
            columns.add(entry.getKey());
            values.add(entry.getValue());
        }
        if (metaModel.isVersioned()) {
            columns.add(metaModel.getVersionColumn());
            values.add(1);
        }

        //TODO: need to invoke checkAttributes here too, and maybe rely on MetaModel for this.

        try {
            boolean containsId = (attributes.get(metaModel.getIdName()) != null); // do not use containsKey
            boolean done;
            String query = metaModel.getDialect().insertParametrized(metaModel, columns, containsId);
            if (containsId) {
                done = (1 == new DB(metaModel.getDbName()).exec(query, values.toArray()));
            } else {
                Object id = new DB(metaModel.getDbName()).execInsert(query, metaModel.getIdName(), values.toArray());
                attributes.put(metaModel.getIdName(), id);
                done = (id != null);
            }
            if (metaModel.cached()) {
                QueryCache.instance().purgeTableCache(metaModel.getTableName());
            }

            if (metaModel.isVersioned()) {
                attributes.put(metaModel.getVersionColumn(), 1);
            }

            dirtyAttributeNames.clear(); // Clear all dirty attribute names as all were inserted. What about versionColumn ?
            fireAfterCreate();

            return done;
        } catch (DBException e) {
            throw e;
        } catch (Exception e) {
            throw new DBException(e.getMessage(), e);
        }
    }

    private void doCreatedAt() {
        if (manageTime && getMetaModelLocal().hasAttribute("created_at")) {
            attributes.put("created_at", new Timestamp(System.currentTimeMillis()));
        }
    }

    private void doUpdatedAt() {
        if (manageTime && getMetaModelLocal().hasAttribute("updated_at")) {
            attributes.put("updated_at", new Timestamp(System.currentTimeMillis()));
        }
    }

    private boolean update() {

        fireBeforeUpdate();
        doUpdatedAt();

        MetaModel metaModel = getMetaModelLocal();
        StringBuilder query = new StringBuilder().append("UPDATE ").append(metaModel.getTableName()).append(" SET ");
        Set<String> attributeNames = metaModel.getAttributeNamesSkipGenerated(manageTime);
        attributeNames.retainAll(dirtyAttributeNames);
        if(attributeNames.size() > 0) {
            join(query, attributeNames, " = ?, ");
            query.append(" = ?");
        }

        List<Object> values = getAttributeValues(attributeNames);

        if (manageTime && metaModel.hasAttribute("updated_at")) {
            if(values.size() > 0)
                query.append(", ");
            query.append("updated_at = ?");
            values.add(get("updated_at"));
        }

        if(metaModel.isVersioned()){
            if(values.size() > 0)
                query.append(", ");
            query.append(getMetaModelLocal().getVersionColumn()).append(" = ?");
            values.add(getLong(getMetaModelLocal().getVersionColumn()) + 1);
        }
        if(values.isEmpty())
            return false;
        query.append(" WHERE ").append(metaModel.getIdName()).append(" = ?");
        values.add(getId());
        if (metaModel.isVersioned()) {
            query.append(" AND ").append(getMetaModelLocal().getVersionColumn()).append(" = ?");
            values.add(get(getMetaModelLocal().getVersionColumn()));
        }
        int updated = new DB(metaModel.getDbName()).exec(query.toString(), values.toArray());
        if(metaModel.isVersioned() && updated == 0){
            throw new StaleModelException("Failed to update record for model '" + getClass() +
                    "', with " + getIdName() + " = " + getId() + " and " + getMetaModelLocal().getVersionColumn()
                    + " = " + get(getMetaModelLocal().getVersionColumn()) +
                    ". Either this record does not exist anymore, or has been updated to have another "
                    + getMetaModelLocal().getVersionColumn() + '.');
        }else if(metaModel.isVersioned()){
            set(getMetaModelLocal().getVersionColumn(), getLong(getMetaModelLocal().getVersionColumn()) + 1);
        }
        if(metaModel.cached()){
            QueryCache.instance().purgeTableCache(metaModel.getTableName());
        }
        dirtyAttributeNames.clear();
        fireAfterUpdate();
        return updated > 0;
    }

    private List<Object> getAttributeValues(Set<String> attributeNames) {
        List<Object> values = new ArrayList<Object>();
        for (String attribute : attributeNames) {
            values.add(get(attribute));
        }
        return values;
    }

    private static <T extends Model> Class<T> modelClass() {
        throw new InitException("failed to determine Model class name, are you sure models have been instrumented?");
    }

    public static String getTableName() {
        return ModelDelegate.tableNameOf(modelClass());
    }

    public Object getId() {
        return get(getIdName());
    }

    public String getIdName() {
        return getMetaModelLocal().getIdName();
    }

    protected void setChildren(Class childClass, List<Model> children) {
        cachedChildren.put(childClass, children);
    }

    /**
     * Turns off automatic management of time-related attributes <code>created_at</code> and <code>updated_at</code>.
     * If management of time attributes is turned off,
     *
     * @param manage if true, the attributes are managed by the model. If false, they are managed by developer.
     */
    public void manageTime(boolean manage) {
        this.manageTime = manage;
    }

    /**
     * Generates INSERT SQL based on this model. Uses the dialect associated with this model database to format the
     * value literals.
     * Example:
     * <pre>
     * String sql = user.toInsert();
     * //yields this output:
     * //INSERT INTO users (id, email, first_name, last_name) VALUES (1, 'mmonroe@yahoo.com', 'Marilyn', 'Monroe')
     * </pre>
     *
     * @return INSERT SQL based on this model.
     */
    public String toInsert() {
        return toInsert(getMetaModelLocal().getDialect());
    }

    /**
     * Generates INSERT SQL based on this model with the provided dialect.
     * Example:
     * <pre>
     * String sql = user.toInsert(new MySQLDialect());
     * //yields this output:
     * //INSERT INTO users (id, email, first_name, last_name) VALUES (1, 'mmonroe@yahoo.com', 'Marilyn', 'Monroe')
     * </pre>
     *
     * @param dialect dialect to be used to generate the SQL
     * @return INSERT SQL based on this model.
     */
    public String toInsert(Dialect dialect) {
        return dialect.insert(getMetaModelLocal(), attributes);
    }
    
    /**
     * Generates UPDATE SQL based on this model. Uses the dialect associated with this model database to format the
     * value literals.
     * Example:
     * <pre>
     * String sql = user.toUpdate();
     * //yields this output:
     * //UPDATE users SET email = 'mmonroe@yahoo.com', first_name = 'Marilyn', last_name = 'Monroe' WHERE id = 1
     * </pre>
     *
     * @return UPDATE SQL based on this model.
     */
    public String toUpdate() {
        return toUpdate(getMetaModelLocal().getDialect());
    }

    /**
     * Generates UPDATE SQL based on this model with the provided dialect.
     * Example:
     * <pre>
     * String sql = user.toUpdate(new MySQLDialect());
     * //yields this output:
     * //UPDATE users SET email = 'mmonroe@yahoo.com', first_name = 'Marilyn', last_name = 'Monroe' WHERE id = 1
     * </pre>
     *
     * @param dialect dialect to be used to generate the SQL
     * @return UPDATE SQL based on this model.
     */
    public String toUpdate(Dialect dialect) {
        return dialect.update(getMetaModelLocal(), attributes);
    }

    /**
     * Generates INSERT SQL based on this model.
     * For instance, for Oracle, the left quote is: "q'{" and the right quote is: "}'".
     * The output will also use single quotes for <code>java.sql.Timestamp</code> and <code>java.sql.Date</code> types.
     *
     * Example:
     * <pre>
     * String insert = u.toInsert("q'{", "}'");
     * //yields this output
     * //INSERT INTO users (id, first_name, email, last_name) VALUES (1, q'{Marilyn}', q'{mmonroe@yahoo.com}', q'{Monroe}');
     * </pre>
     * @param leftStringQuote - left quote for a string value, this can be different for different databases.
     * @param rightStringQuote - left quote for a string value, this can be different for different databases.
     * @return SQL INSERT string;
     * @deprecated Use {@link #toInsert(Dialect)} instead.
     */
    @Deprecated
    public String toInsert(String leftStringQuote, String rightStringQuote){
        return toInsert(new SimpleFormatter(java.sql.Date.class, "'", "'"),
                        new SimpleFormatter(Timestamp.class, "'", "'"),
                        new SimpleFormatter(String.class, leftStringQuote, rightStringQuote));
    }

    /**
     * @param formatters
     * @return
     * @deprecated Use {@link #toInsert(Dialect)} instead.
     */
    @Deprecated
    public String toInsert(Formatter... formatters){
        HashMap<Class, Formatter> formatterMap = new HashMap<Class, Formatter>();

        for(Formatter f: formatters){
            formatterMap.put(f.getValueClass(), f);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(getMetaModelLocal().getTableName()).append(" (");
        join(sb, attributes.keySet(), ", ");
        sb.append(") VALUES (");
        Iterator<Object> it = attributes.values().iterator();
        while (it.hasNext()) {
            Object value = it.next();
            if (value == null) {
                sb.append("NULL");
            }
            else if (value instanceof String && !formatterMap.containsKey(String.class)){
                sb.append('\'').append(value).append('\'');
            }else{
                if(formatterMap.containsKey(value.getClass())){
                    sb.append(formatterMap.get(value.getClass()).format(value));
                }else{
                    sb.append(value);
                }
            }
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Use to force-purge cache associated with this table. If this table is not cached, this method has no side effect.
     */
    public static void purgeCache(){
        ModelDelegate.purgeCache(modelClass());
    }

    /**
     * Convenience method: converts ID value to Long and returns it.
     *
     * @return value of attribute corresponding to <code>getIdName()</code>, converted to Long.
     */
    public Long getLongId() {
        Object id = getId();
        if (id == null) {
            throw new NullPointerException(getIdName() + " is null, cannot convert to Long");
        }
        return Convert.toLong(id);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(attributes);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        attributes = new CaseInsensitiveMap<Object>();
        attributes.putAll((Map<String, Object>) in.readObject());
    }
}
