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

import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.CompositePK;
import org.javalite.activejdbc.associations.*;
import org.javalite.activejdbc.cache.QueryCache;
import org.javalite.common.*;
import org.javalite.conversion.BlankToNullConverter;
import org.javalite.conversion.Converter;
import org.javalite.conversion.ZeroToNullConverter;
import org.javalite.activejdbc.dialects.Dialect;
import org.javalite.activejdbc.logging.LogFilter;
import org.javalite.activejdbc.logging.LogLevel;

import org.javalite.json.JSONHelper;
import org.javalite.validation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.*;
import java.util.Collections;
import java.util.Map.Entry;

import static org.javalite.activejdbc.ModelDelegate.metaModelFor;
import static org.javalite.activejdbc.ModelDelegate.metaModelOf;
import static org.javalite.common.Inflector.*;
import static org.javalite.common.Util.*;

/**
 * This class is a super class of all "models" and provides most functionality
 * necessary for implementation of Active Record pattern.
 *
 * @author Igor Polevoy
 * @author Eric Nielsen
 */
public abstract class Model extends CallbackSupport implements Externalizable, Validatable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Model.class);

    private Map<String, Object> attributes = new CaseInsensitiveMap<>();
    private Set<String> dirtyAttributeNames = new CaseInsensitiveSet();
    private boolean frozen;
    private MetaModel metaModelLocal;
    private ModelRegistry modelRegistryLocal;
    private final Map<Class, Model> cachedParents = new HashMap<>();
    private final Map<Class, List<Model>> cachedChildren = new HashMap<>();
    private boolean manageTime = true;
    private boolean compositeKeyPersisted;
    private Errors errors = new Errors();

    protected Model() {
        metaModelLocal = metaModelOf(getClass());
        modelRegistryLocal = Registry.instance().modelRegistryOf(this.getClass());
    }

    protected Model(MetaModel metaModel, ModelRegistry modelRegistry) {
        this.metaModelLocal = Objects.requireNonNull(metaModel, "metaModel is null");
        this.modelRegistryLocal = Objects.requireNonNull(modelRegistry, "modelRegistry is null");
    }

    private void fireAfterLoad() {
        afterLoad();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.afterLoad(this);
        }
    }

    private void fireBeforeSave() {
        beforeSave();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.beforeSave(this);
        }
    }

    private void fireAfterSave() {
        afterSave();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.afterSave(this);
        }
    }

    private void fireBeforeCreate() {
        beforeCreate();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.beforeCreate(this);
        }
    }

    private void fireAfterCreate() {
        afterCreate();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.afterCreate(this);
        }
    }

    private void fireBeforeUpdate() {
        beforeUpdate();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.beforeUpdate(this);
        }
    }

    private void fireAfterUpdate() {
        afterUpdate();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.afterUpdate(this);
        }
    }

    private void fireBeforeDelete() {
        beforeDelete();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.beforeDelete(this);
        }
    }

    private void fireAfterDelete() {
        afterDelete();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.afterDelete(this);
        }
    }

    private void fireBeforeValidation() {
        beforeValidation();
        for(CallbackListener callback: modelRegistryLocal.callbacks())
            callback.beforeValidation(this);
    }

    private void fireAfterValidation() {
        afterValidation();
        for (CallbackListener callback : modelRegistryLocal.callbacks()) {
            callback.afterValidation(this);
        }
    }

    /**
     * <p>
     * Provides {@link MetaModel} object related to this model class.
     * </p>
     * Synonym of {@link #metaModel()}.
     *
     * @return {@link MetaModel} object related to this model class.
     */
    public static MetaModel getMetaModel() {
        return metaModelOf(modelClass());
    }

    /**
     * Synonym of {@link #getMetaModel()}.
     *
     * @return {@link MetaModel} of this model.
     */
    public static MetaModel metaModel() {
        return metaModelOf(modelClass());
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
        Set<String> changedAttributeNames = hydrate(input, false);
        dirtyAttributeNames.addAll(changedAttributeNames);
        return (T) this;
    }

    /**
     * A convenience method to fetch existing model from db or to create and insert new record with attribute values.
     *
     * @param namesAndValues names and values. elements at indexes 0, 2, 4, 8... are attribute names, and elements at
     * indexes 1, 3, 5... are values. Element at index 1 is a value for attribute at index 0 and so on.
     *
     *@return Model fetched from the db or newly created and saved instance.
     */
    public static <T extends Model> T findOrCreateIt(Object... namesAndValues) {
        return ModelDelegate.findOrCreateIt(modelClass(), namesAndValues);
    }

    /**
     * A convenience method to fetch existing model from db or to create a new instance in memory initialized with
     * some attribute values.
     *
     * @param namesAndValues names and values. elements at indexes 0, 2, 4, 8... are attribute names, and elements at
     * indexes 1, 3, 5... are values. Element at index 1 is a value for attribute at index 0 and so on.
     *
     *@return Model fetched from the db or newly created and initialized object.
     */
    public static <T extends Model> T findOrInit(Object... namesAndValues) {
        return ModelDelegate.findOrInit(modelClass(), namesAndValues);
    }

    /**
     * Hydrates a this instance of model from a map. Only picks values from a map that match
     * this instance's attribute names, while ignoring the others.
     *
     * @param attributesMap
     * @param fireAfterLoad
     * @return the set of changed (i.e. dirty) attribute names
     */
    protected Set<String> hydrate(Map<String, Object> attributesMap, boolean fireAfterLoad) {

        Set<String> changedAttributeNames = new HashSet<>();
        Set<String> attributeNames = metaModelLocal.getAttributeNames();
        for (Map.Entry<String, Object> entry : attributesMap.entrySet()) {
            if (attributeNames.contains(entry.getKey()) ) {

                if (entry.getValue() instanceof Clob && metaModelLocal.cached()) {
                    String convertedString = Convert.toString(entry.getValue());
                    if (willAttributeModifyModel(entry.getKey(), convertedString)) {
                        this.attributes.put(entry.getKey(), convertedString);
                        changedAttributeNames.add(entry.getKey());
                    }
                } else {
                    Object convertedObject = metaModelLocal.getDialect().overrideDriverTypeConversion(
                            metaModelLocal, entry.getKey(), entry.getValue());
                    if (willAttributeModifyModel(entry.getKey(), convertedObject)) {
                        this.attributes.put(entry.getKey(), convertedObject);
                        changedAttributeNames.add(entry.getKey());
                    }
                }
            }
        }
        if (getCompositeKeys() != null){
            compositeKeyPersisted = true;
        }
        if(fireAfterLoad){
            fireAfterLoad();
        }

        return changedAttributeNames;
    }

    /**
     * Verifies if the passed value for attributeName will set this instance to modified state.
     */
    private boolean willAttributeModifyModel(String attributeName, Object newValue) {

        Object currentValue = get(attributeName);
        return currentValue != null ? !currentValue.equals(newValue) : newValue != null;
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
        Converter<Object, java.sql.Date> converter = modelRegistryLocal.converterForValue(attributeName, value, java.sql.Date.class);
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
        Converter<Object, java.sql.Date> converter = modelRegistryLocal.converterForValue(
                attributeName, value, java.sql.Date.class);
        return converter != null ? converter.convert(value) : Convert.toSqlDate(value);
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
        if(isFrozen()){
            throw new FrozenException(this);
        }
        Converter<Object, Object> converter = modelRegistryLocal.converterForValue(attributeName, value, Object.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : value);
    }

    /**
     * Sets raw value of an attribute, without applying conversions.
     */
    private <T extends Model> T setRaw(String attributeName, Object value) {
        if (manageTime && attributeName.equalsIgnoreCase("created_at")) {
            throw new IllegalArgumentException("cannot set 'created_at'");
        }
        metaModelLocal.checkAttribute(attributeName);

        if (willAttributeModifyModel(attributeName, value)) {
            attributes.put(attributeName, value);
            dirtyAttributeNames.add(attributeName);
        }
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
        return getId() == null && !compositeKeyPersisted;
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
        int result;
        if (getCompositeKeys() != null) {
            String[] compositeKeys = getCompositeKeys();
            StringBuilder query = new StringBuilder();
            Object[] values = new Object[compositeKeys.length];
            for (int i = 0; i < compositeKeys.length; i++) {
                query.append(i == 0 ? "DELETE FROM " + metaModelLocal.getTableName() + " WHERE " : " AND ").append(compositeKeys[i]).append(" = ?");
                values[i] = get(compositeKeys[i]);
            }
            result = new DB(metaModelLocal.getDbName()).exec(query.toString(), values);
        } else {
            StringBuilder query = new StringBuilder("DELETE FROM ").append(metaModelLocal.getTableName()).append(" WHERE ").append(getIdName()).append("        = ?");
            List<Object> values = new ArrayList<>();
            values.add(getId());

            if(metaModelLocal.hasPartitionIDs()){
                for (String partitionId : metaModelLocal.getPartitionIDs()) {
                    query.append(" AND ").append(partitionId).append(" = ?");
                    values.add(get(partitionId));
                }
            }


            result = new DB(metaModelLocal.getDbName()).exec(query.toString(), values.toArray());
        }
        if (1 == result) {
            frozen = true;
            if (metaModelOf(getClass()).cached()) {
                Registry.cacheManager().purgeTableCache(metaModelLocal);
            }
            ModelDelegate.purgeEdges(metaModelLocal);
            fireAfterDelete();
            return true;
        }
        fireAfterDelete();
        return false;
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
     * <p></p>
     * In cases of simple one to many and polymorphic associations, things are as expected, a parent is deleted an all children are
     * deleted as well, but in more complicated cases, this method will walk entire three of associated tables, sometimes
     * coming back to the same one where it all started.
     * It will follow associations of children and their associations too; consider this a true cascade delete with all implications
     * (circular dependencies, referential integrity constraints, potential performance bottlenecks, etc.)
     * <p></p>
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
     * <p></p>
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
     * <p></p>
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
     * @see #deleteCascade()
     * @param excludedAssociations associations
     */
    public void deleteCascadeExcept(Association... excludedAssociations){
        deleteMany2ManyDeep(metaModelLocal.getManyToManyAssociations(excludedAssociations), excludedAssociations);
        deleteChildrenDeep(metaModelLocal.getOneToManyAssociations(excludedAssociations), excludedAssociations);
        deleteChildrenDeep(metaModelLocal.getPolymorphicAssociations(excludedAssociations), excludedAssociations);
        delete();
    }



    private void deleteMany2ManyDeep(List<Many2ManyAssociation> many2ManyAssociations, Association... excludedAssociations){
        List<Model>  allMany2ManyChildren = new ArrayList<>();
        for (Association association : many2ManyAssociations) {
            Class<? extends Model> targetModelClass = association.getTargetClass();
            allMany2ManyChildren.addAll(getAll(targetModelClass));
        }

        deleteJoinsForManyToMany();
        for (Model model : allMany2ManyChildren) {
            model.deleteCascadeExcept(excludedAssociations);
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
     * <p></p>
     * After deletion, this instance becomes {@link #frozen()} and cannot be used anymore until {@link #thaw()} is called.
     */
    public void deleteCascadeShallow(){
        deleteJoinsForManyToMany();
        deleteOne2ManyChildrenShallow();
        deletePolymorphicChildrenShallow();
        delete();
    }


    private void deleteJoinsForManyToMany() {
        List<? extends Many2ManyAssociation> associations = metaModelLocal.getManyToManyAssociations();
        for (Many2ManyAssociation association : associations) {
            deleteManyToManyLinks(association);
        }
    }

    /**
     * Deletes all records from a join table related to this model.
     *
     * @param association association  to another table.
     */
    private void deleteManyToManyLinks(Many2ManyAssociation association){
        String join = association.getJoin();
        String sourceFK = association.getSourceFkName();
        new DB(metaModelLocal.getDbName()).exec("DELETE FROM " + join + " WHERE " + sourceFK + " = ?", getId());
    }


    private void deleteOne2ManyChildrenShallow() {
        List<OneToManyAssociation> childAssociations = metaModelLocal.getOneToManyAssociations();
        for (OneToManyAssociation association : childAssociations) {
            deleteOne2ManyChildrenShallow(association);
        }
    }

    /**
     * Deletes immediate children.
     */
    private void deleteOne2ManyChildrenShallow(OneToManyAssociation association){
        String targetTable = metaModelOf(association.getTargetClass()).getTableName();
        new DB(metaModelLocal.getDbName()).exec("DELETE FROM " + targetTable + " WHERE " + association.getFkName() + " = ?", getId());
    }

    private void deletePolymorphicChildrenShallow() {
        List<OneToManyPolymorphicAssociation> polymorphics = metaModelLocal.getPolymorphicAssociations();
        for (OneToManyPolymorphicAssociation association : polymorphics) {
            deletePolymorphicChildrenShallow(association);
        }
    }

    /**
     * Deletes immediate polymorphic children
     */
    private void deletePolymorphicChildrenShallow(OneToManyPolymorphicAssociation association){
        String targetTable = metaModelOf(association.getTargetClass()).getTableName();
        String parentType = association.getTypeLabel();
        new DB(metaModelLocal.getDbName()).exec("DELETE FROM " + targetTable + " WHERE parent_id = ? AND parent_type = ?", getId(), parentType);
    }

    private void deleteChildrenDeep(List<? extends Association> childAssociations, Association... excludedAssociations){
        for (Association association : childAssociations) {
            String targetTableName = metaModelOf(association.getTargetClass()).getTableName();
            Class c = Registry.instance().getModelClass(targetTableName, false);
            if(c == null){// this model is probably not defined as a class, but the table exists!
                LogFilter.log(LOGGER, LogLevel.ERROR, "ActiveJDBC WARNING: failed to find a model class for: {}, maybe model is not defined for this table?"
                                + " There might be a risk of running into integrity constrain violation if this model is not defined.",
                        targetTableName);
            }
            else{
                List<Model> dependencies = getAll(c);
                for (Model model : dependencies) {
                    model.deleteCascadeExcept(excludedAssociations);
                }
            }
        }
    }

    /**
     * Deletes immediate children (does not walk the dependency tree).
     * If you have integrity constraints in the DB that are not accounted
     * by this call, you  will get DB exceptions.
     * <p>
     * <h4>One to many and polymorphic associations</h4>
     * Deletes all child records.
     * <h4>Many to many associations</h4>
     * Deletes links in a join table. Nothing else is deleted.
     * </p>
     *
     * @param clazz type of a child to delete
     */
    public <T extends Model> void deleteChildrenShallow(Class<T> clazz) {
        List<Association> associations = metaModelLocal.getAssociationsForTarget(clazz);
        for (Association association : associations) {
            if (association instanceof OneToManyAssociation) {
                deleteOne2ManyChildrenShallow((OneToManyAssociation) association);
            }else if(association instanceof Many2ManyAssociation){
                deleteManyToManyLinks((Many2ManyAssociation) association);
            }else if(association instanceof OneToManyPolymorphicAssociation){
                deletePolymorphicChildrenShallow((OneToManyPolymorphicAssociation) association);
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
        return null != new DB(metaModelLocal.getDbName()).firstCell(metaModelLocal.getDialect().selectExists(metaModelLocal), getId());
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
     * <p></p>
     * If {@link LazyList#include(Class[])} method was used, and this
     * model belongs to a parent (as in many to one relationship), then the parent
     * will be eagerly loaded and also converted to a map. Parents' maps are keyed in the
     * returned map by underscored name of a parent model class name.
     * <p></p>
     * For example, if this model were <code>Address</code>
     * and a parent is <code>User</code> (and user has many addresses), then the resulting map would
     * have all the attributes of the current table and another map representing a parent user with a
     * key "user" in current map.
     *
     * @return all values of the model with all attribute names converted to lower case.
     */
    public Map<String, Object> toMap(){
        Map<String, Object> retVal = new HashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            Object v = entry.getValue();
            if (v != null) {
                if (v instanceof Clob) {
                    retVal.put(entry.getKey().toLowerCase(), Convert.toString(v));
                } else {
                    retVal.put(entry.getKey().toLowerCase(), v);
                }
            }

        }
        for(Entry<Class, Model> parent: cachedParents.entrySet()){
            retVal.put(underscore(parent.getKey().getSimpleName()), parent.getValue().toMap());
        }

        for(Entry<Class, List<Model>> cachedChild: cachedChildren.entrySet()){
            List<Model> children = cachedChild.getValue();

            List<Map> childMaps = new ArrayList<>(children.size());
            for(Model child:children){
                childMaps.add(child.toMap());
            }
            retVal.put(tableize(cachedChild.getKey().getSimpleName()), childMaps);
        }
        return retVal;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("Model: ").append(getClass().getName())
                .append(", table: '").append(metaModelLocal.getTableName())
                .append("', attributes: ").append(attributes);

        if (cachedParents.size() > 0) {
            sb.append(", parent: ").append(cachedParents);
        }

        if (cachedChildren.size() > 0) {
            sb.append(", children: ").append(cachedChildren);
        }
        return sb.toString();
    }


    /**
     * Parses XML into a model. It expects the same structure of XML as the method {@link #toXml(boolean, boolean, String...)}.
     * It ignores children and dependencies (for now) if any. This method  will parse the model attributes
     * from the XML document, and will then call {@link #fromMap(java.util.Map)} method. It does not save data into a database, just sets the
     * attributes.
     *
     * @param xml xml to read model attributes from.
     */
    public void fromXml(String xml) {
        try {
            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xml.getBytes()));
            String attr = null;
            String chars = null;
            Map<Object, Object> res = new HashMap<>();
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        attr = reader.getLocalName();
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        chars = reader.getText().trim();;
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        if (attr != null && !blank(chars)) {
                            res.put(attr, chars);
                        }
                        attr = chars = null;
                        break;
                }
            }
            fromMap(res);
        } catch (XMLStreamException e) {
            throw new InitException(e);
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
        for (Entry<Class, List<Model>> cachedChild : cachedChildren.entrySet()) {
            if (pretty) { sb.append("  ").append(indent); }
            String tag = pluralize(underscore(cachedChild.getKey().getSimpleName()));
            sb.append('<').append(tag).append('>');
            if (pretty) { sb.append('\n'); }
            for (Model child : cachedChild.getValue()) {
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
     * Override in a subclass to inject custom content onto XML just before the closing tag.
     *
     * <p>To keep the formatting, it is recommended to implement this method as the example below.
     *
     * <blockquote><pre>
     * if (pretty) { sb.append(indent); }
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
            Object attribute = attributes.get(name);
            if (attribute == null) {
                sb.append("null");
            } else if (attribute instanceof Number || attribute instanceof Boolean) {
                sb.append(attribute);
            } else if (attribute instanceof Date) {
                sb.append('"').append(Convert.toIsoString((Date) attribute)).append('"');
            } else {
                sb.append('"');
                sb.append(JSONHelper.sanitize(Convert.toString(attribute)));
                sb.append('"');
            }
        }

        if (cachedParents.size() > 0) {

            sb.append(',');
            if (pretty) { sb.append("\n  ").append(indent); }
            sb.append("\"parents\":{");

            List<Class> parentClasses = new ArrayList<>();
            parentClasses.addAll(cachedParents.keySet());
            for (int i = 0; i < parentClasses.size(); i++) {
                if (i > 0) { sb.append(','); }
                Class parentClass = parentClasses.get(i);
                String name = pluralize(parentClasses.get(i).getSimpleName()).toLowerCase();
                if (pretty) { sb.append("\n    ").append(indent); }
                sb.append('"').append(name).append("\":[");
                Model parent = cachedParents.get(parentClass);
                if (pretty) {
                    sb.append('\n');
                }
                parent.toJsonP(sb, pretty, (pretty ? "      " + indent : ""));

                if (pretty) {
                    sb.append("\n    ").append(indent);
                }
                sb.append(']');
            }
            if (pretty) { sb.append("\n  ").append(indent); }
            sb.append('}');
        }

        if (cachedChildren.size() > 0) {

            sb.append(',');
            if (pretty) { sb.append("\n  ").append(indent); }
            sb.append("\"children\":{");

            List<Class> childClasses = new ArrayList<>();
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
     * @return instance of a parent of this instance in the "belongs to"  relationship if found, ot null if not found.
     */
    public <P extends Model> P parent(Class<P> parentClass) {
        return parent(parentClass, false);
    }

    /**
     * Same as {@link #parent(Class)}, with additional argument.
     *
     * @param parentClass class of a parent model
     * @param cache true to also cache a found instance for future reference.
     * @return instance of a parent of this instance in the "belongs to"  relationship if found, ot null if not found.
     */
    public <P extends Model> P parent(Class<P> parentClass, boolean cache) {
        P cachedParent = parentClass.cast(cachedParents.get(parentClass));
        if (cachedParent != null) {
            return cachedParent;
        }

        BelongsToAssociation ass = metaModelLocal.getAssociationForTarget(parentClass, BelongsToAssociation.class);
        BelongsToPolymorphicAssociation assP = metaModelLocal.getAssociationForTarget(parentClass, BelongsToPolymorphicAssociation.class);

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
            throw new IllegalArgumentException("there is no association with model: " + parentClass);
        }

        if (fkValue == null) {
            LogFilter.log(LOGGER, LogLevel.DEBUG, "Attribute: {} is null, cannot determine parent. Child record: {}", fkName, this);
            return null;
        }

        MetaModel parentMM = metaModelOf(parentClass);
        String parentTable = parentMM.getTableName();
        String parentIdName = parentMM.getIdName();
        String query = metaModelLocal.getDialect().selectStarParametrized(parentTable, parentIdName);

        if (parentMM.cached()) {
            P parent = parentClass.cast(QueryCache.instance().getItem(parentTable, query, new Object[]{fkValue}));
            if (parent != null) {
                LogFilter.logQuery(LOGGER, query, new Object[]{fkValue}, System.currentTimeMillis(), true);
                return parent;
            }
        }

        List<Map> results = new DB(parentMM.getDbName()).findAll(query, fkValue);
        //expect only one result here
        if (results.isEmpty()) { //this should be covered by referential integrity constraint
            return null;
        } else {
            try {
                P parent = parentClass.getDeclaredConstructor().newInstance();
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
        List<Association> associations = metaModelLocal.getAssociations();
        for (Association association : associations) {
            if (association instanceof BelongsToAssociation && association.getTargetClass().equals(parent.metaModelLocal.getModelClass())) {
                set(((BelongsToAssociation)association).getFkName(), parent.getId());
                return;
            }
            if(association instanceof BelongsToPolymorphicAssociation && association.getTargetClass().equals(parent.metaModelLocal.getModelClass())){
                set("parent_id", parent.getId());
                set("parent_type", ((BelongsToPolymorphicAssociation)association).getTypeLabel());
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(parent.getClass()).append(" is not associated with ").append(this.getClass())
                .append(", list of existing associations:\n");
        join(sb, metaModelLocal.getAssociations(), "\n");
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
        if (!metaModelLocal.getTableName().equals(other.metaModelLocal.getTableName())) {
            throw new IllegalArgumentException("can only copy between the same types");
        }
        Map<String, Object> otherAttributes = other.getAttributes();
        for (String name : metaModelLocal.getAttributeNamesSkipId()) {
            attributes.put(name, otherAttributes.get(name));
            dirtyAttributeNames.add(name);
            // Why not use setRaw() here? Does the same and avoids duplication of code... (Garagoth)
            // other.setRaw(name, getRaw(name));
        }
    }

    /**
     * Re-reads all attribute values from DB. Will invalidate cache and will force a trip to the database.
     *
     */
    public void refresh() {
        QueryCache.instance().purgeTableCache(metaModelLocal);
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

        if (metaModelLocal.hasAttribute(attributeName)) {
            Object value = attributes.get(attributeName);
            Converter<Object, Object> converter = modelRegistryLocal.converterForValue(attributeName, value, Object.class);
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
                    metaModelLocal.checkAttribute(attributeName);
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
        if(frozen){
            throw new FrozenException(this);
        }

        if(attributeName == null) {
            throw new IllegalArgumentException("attributeName cannot be null");
        }
        metaModelLocal.checkAttribute(attributeName);
        return attributes.get(attributeName);
    }


    private Object tryPolymorphicParent(String parentTable){
        MetaModel parentMM = inferTargetMetaModel(parentTable);
        if(parentMM == null){
            return null;
        }else
            return metaModelLocal.hasAssociation(parentMM.getModelClass(), BelongsToPolymorphicAssociation.class) ?
                    parent(parentMM.getModelClass()): null;
    }

    private Object tryParent(String parentTable){
        MetaModel parentMM = inferTargetMetaModel(parentTable);
        if(parentMM == null){
            return null;
        }else
            return metaModelLocal.hasAssociation(parentMM.getModelClass(), BelongsToAssociation.class) ?
                    parent(parentMM.getModelClass()): null;
    }

    private Object tryPolymorphicChildren(String childTable){
        MetaModel childMM = inferTargetMetaModel(childTable);
        if(childMM == null){
            return null;
        }else
            return metaModelLocal.hasAssociation(childMM.getModelClass(), OneToManyPolymorphicAssociation.class) ?
                    getAll(childMM.getModelClass()): null;
    }

    private Object tryChildren(String childTable){
        MetaModel childMM = inferTargetMetaModel(childTable);
        if(childMM == null){
            return null;
        }else
            return metaModelLocal.hasAssociation(childMM.getModelClass(), OneToManyAssociation.class) ?
                    getAll(childMM.getModelClass()): null;
    }

    private Object tryOther(String otherTable){
        MetaModel otherMM = inferTargetMetaModel(otherTable);
        if(otherMM == null){
            return null;
        }else
            return metaModelLocal.hasAssociation(otherMM.getModelClass(), Many2ManyAssociation.class) ?
                    getAll(otherMM.getModelClass()): null;
    }

    private MetaModel inferTargetMetaModel(String targetTableName){
        String targetTable = singularize(targetTableName);
        MetaModel targetMM = metaModelFor(targetTable);
        if(targetMM == null){
            targetTable = pluralize(targetTableName);
            targetMM = metaModelFor(targetTable);
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
        Converter<Object, String> converter = modelRegistryLocal.converterForValue(attributeName, value, String.class);
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
        Converter<Object, BigDecimal> converter = modelRegistryLocal.converterForValue(
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
        Converter<Object, Integer> converter = modelRegistryLocal.converterForValue(attributeName, value, Integer.class);
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
        Converter<Object, Long> converter = modelRegistryLocal.converterForValue(attributeName, value, Long.class);
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
        Converter<Object, Short> converter = modelRegistryLocal.converterForValue(attributeName, value, Short.class);
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
        Converter<Object, Float> converter = modelRegistryLocal.converterForValue(attributeName, value, Float.class);
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
        Converter<Object, Time> converter = modelRegistryLocal.converterForValue(
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
        Converter<Object, Timestamp> converter = modelRegistryLocal.converterForValue(
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
        Converter<Object, Double> converter = modelRegistryLocal.converterForValue(attributeName, value, Double.class);
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
        Converter<Object, Boolean> converter = modelRegistryLocal.converterForValue(attributeName, value, Boolean.class);
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
        Converter<Object, String> converter = modelRegistryLocal.converterForValue(attributeName, value, String.class);
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
        Converter<Object, BigDecimal> converter = modelRegistryLocal.converterForValue(
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
        Converter<Object, Short> converter = modelRegistryLocal.converterForValue(attributeName, value, Short.class);
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
        Converter<Object, Integer> converter = modelRegistryLocal.converterForValue(attributeName, value, Integer.class);
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
        Converter<Object, Long> converter = modelRegistryLocal.converterForValue(attributeName, value, Long.class);
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
        Converter<Object, Float> converter = modelRegistryLocal.converterForValue(attributeName, value, Float.class);
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
        Converter<Object, Time> converter = modelRegistryLocal.converterForValue(
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
        Converter<Object, Timestamp> converter = modelRegistryLocal.converterForValue(
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
        Converter<Object, Double> converter = modelRegistryLocal.converterForValue(attributeName, value, Double.class);
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
        Converter<Object, Boolean> converter = modelRegistryLocal.converterForValue(attributeName, value, Boolean.class);
        return setRaw(attributeName, converter != null ? converter.convert(value) : Convert.toBoolean(value));
    }

    /**
     * This methods supports one to many, many to many relationships as well as polymorphic associations.
     * <p></p>
     * In case of one to many, the <code>clazz</code>  must be a class of a child model, and it will return a
     * collection of all children.
     * <p></p>
     * In case of many to many, the <code>clazz</code>  must be a class of a another related model, and it will return a
     * collection of all related models.
     * <p></p>
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

//        String tableName = Registry.instance().getTableName(clazz);
//        if(tableName == null) throw new IllegalArgumentException("table: " + tableName + " does not exist for model: " + clazz);

        return get(clazz, null);
    }


    /**
     * Provides a list of child models in one to many, many to many and polymorphic associations, but in addition also allows to filter this list
     * by criteria.
     *
     * <p></p>
     * <strong>1.</strong> For one to many, the criteria is against the child table.
     *
     * <p></p>
     * <strong>2.</strong> For polymorphic association, the criteria is against the child table.
     *
     * <p></p>
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
     * @param targetModelClass related type
     * @param criteria sub-query for join table.
     * @param params parameters for a sub-query
     * @return list of relations in many to many
     */
    public <C extends Model> LazyList<C> get(Class<C> targetModelClass, String criteria, Object ... params){

        OneToManyAssociation oneToManyAssociation = metaModelLocal.getAssociationForTarget(targetModelClass, OneToManyAssociation.class);
        MetaModel mm = metaModelLocal;

        Many2ManyAssociation manyToManyAssociation = metaModelLocal.getAssociationForTarget(targetModelClass, Many2ManyAssociation.class);
        OneToManyPolymorphicAssociation oneToManyPolymorphicAssociation = metaModelLocal.getAssociationForTarget(targetModelClass, OneToManyPolymorphicAssociation.class);

        String additionalCriteria =  criteria != null? " AND ( " + criteria + " ) " : "";
        String subQuery;

        String targetId = metaModelOf(targetModelClass).getIdName();
        MetaModel targetMM = metaModelOf(targetModelClass);
        String targetTable = targetMM.getTableName();


        if (oneToManyAssociation != null) {
            subQuery = oneToManyAssociation.getFkName() + " = ? " + additionalCriteria;
        } else if (manyToManyAssociation != null) {
            String joinTable = manyToManyAssociation.getJoin();
            String query = "SELECT " + targetTable + ".* FROM " + targetTable + ", " + joinTable +
                    " WHERE " + targetTable + "." + targetId + " = " + joinTable + "." + manyToManyAssociation.getTargetFkName() +
                    " AND " + joinTable + "." + manyToManyAssociation.getSourceFkName() + " = ? " + additionalCriteria;

            Object[] allParams = new Object[params.length + 1];
            allParams[0] = getId();
            System.arraycopy(params, 0, allParams, 1, params.length);
            return new LazyList<>(true, metaModelOf(manyToManyAssociation.getTargetClass()), query, allParams);
        } else if (oneToManyPolymorphicAssociation != null) {
            subQuery = "parent_id = ? AND " + " parent_type = '" + oneToManyPolymorphicAssociation.getTypeLabel() + "'" + additionalCriteria;
        } else {
            throw new NotAssociatedException(metaModelLocal.getModelClass(), targetModelClass);
        }

        Object[] allParams = new Object[params.length + 1];
        allParams[0] = getId();
        System.arraycopy(params, 0, allParams, 1, params.length);
        return new LazyList<>(subQuery, targetMM, allParams);
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
     * Use in a static block of a model definotion to add a scope.
     *
     * @param name name of scope
     * @param criteria SQL criteria for the scope filter
     */
    protected static void addScope(String name, String criteria) {
        ModelDelegate.addScope(modelClass().getName(), name, criteria);
    }

    /**
     * Adds a new error to the collection of errors. This is a convenience method to be used from custom validators.
     *
     * @param key - key wy which this error can be retrieved from a collection of errors: {@link #errors()}.
     * @param value - this is a key of the message in the resource bundle.
     * @see Messages
     */
    public void addError(String key, String value){
        errors.put(key, value);
    }

    /**
     * Removes a validator from model.
     *
     * @param validator validator to remove. It needs to be an exact reference validator instance to
     *                  remove. If argument was not added to this model before, this method will
     *                  do nothing.
     */
    public static void removeValidator(Validator validator){
        ModelDelegate.removeValidator(modelClass(), validator);
    }

    public static List<Validator> getValidators(Class<? extends Model> clazz) {
        return ModelDelegate.validatorsOf(clazz);
    }

    /**
     * Validates an attribite format with a ree hand regular expression.
     *
     * @param attributeName attribute to validate.
     * @param pattern regexp pattern which must match  the value.
     */
    protected static ValidationBuilder validateRegexpOf(String attributeName, String pattern) {
        return ModelDelegate.validateRegexpOf(modelClass(), attributeName, pattern);
    }

    /**
     * Validates email format.
     *
     * @param attributeName name of attribute that holds email value.
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
     */
    protected static ValidationBuilder validateRange(String attributeName, Number min, Number max) {
        return ModelDelegate.validateRange(modelClass(), attributeName, min, max);
    }

    /**
     * The validation will not pass if the value is either an empty string "", or null.
     *
     * @param attributeNames list of attributes to validate.
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
     * Registers a custom converter for the specified attributes.
     *
     * @param converter custom converter
     * @param attributeNames attribute names
     */
    protected static void convertWith(Converter converter, String... attributeNames) {
        ModelDelegate.convertWith(modelClass(), converter, attributeNames);
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
     * Sets  lifecycle listeners on current model. All previous listeners will be unregistered.
     * In case you are using cache in the current model, it is recommended to reset this model's caches before calling this method
     * in order to avoid a potential logical error.
     *
     * @param listeners list of lifecycle listeners
     */
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
        validate(true);
    }


    /**
     * Executes all validators attached to this model.
     */
    public void validate(boolean reset) {

        if(reset){
            errors = new Errors();
        }

        fireBeforeValidation();


        List<Validator> validators = modelRegistryLocal.validators();
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
    public void addFailedValidator(Validator validator, String errorKey) {
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
     * Composite PK values in exactly the same order as specified  in {@link CompositePK}.
     *
     * @param values  Composite PK values in exactly the same order as specified  in {@link CompositePK}.
     * @return instance of a found model, or null if nothing found.
     * @see CompositePK
     */
    public static <T extends Model> T findByCompositeKeys(Object... values) {
        return ModelDelegate.findByCompositeKeys(Model.<T>modelClass(), values);
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
     * Allows to specify multiple scopes as filters such as:
     *
     * <pre>
     *     List<Employee> activeDevelopers = Employee.scopes("developers", "active").all();
     * </pre>
     *
     * @param scopes list of scopes to use as filters.
     *
     * @return selected objects from database based on scope filters.
     */
    public static <T extends Model> ScopeBuilder<T> scopes(String ... scopes) {
        return new ScopeBuilder<T>(modelClass(), scopes);
    }

    /**
     * Allows to specify multiple scopes as filters such as:
     *
     * <pre>
     *     List<Employee> developers = Employee.scope("developers").all();
     * </pre>
     *
     * @param scope a scope to use as a filter.
     *
     * @return selected objects from database based on scope filters.
     */
    public static <T extends Model> ScopeBuilder<T> scope(String  scope) {
        return new ScopeBuilder<T>(modelClass(), new String[]{scope});
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
     * Ensure that the query returns all columns associated with this model, so that the resulting models could hydrate themselves properly.
     * Returned columns that are not part of this model will be ignored, but can be used for clauses like above.
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

        if (child == null) {
            throw new IllegalArgumentException("cannot add what is null");
        }

        MetaModel childMetaModel = metaModelOf(child.getClass());
        MetaModel metaModel = metaModelLocal;
        if (getId() != null) {
            if (metaModel.hasAssociation(child.getClass(), OneToManyAssociation.class)) {
                addOne2ManyChild(metaModel, child);
            }else if(metaModel.hasAssociation(child.getClass(), Many2ManyAssociation.class)){
                addMany2ManyChild(metaModel, child, childMetaModel);
            } else if(metaModel.hasAssociation(child.getClass(), OneToManyPolymorphicAssociation.class)) {
                addPolymorphicChild(metaModel, child);
            }else
                throw new NotAssociatedException(getClass(), child.getClass());
        } else {
            throw new IllegalArgumentException("You can only add associated model to an instance that exists in DB. Save this instance first, then you will be able to add dependencies to it.");
        }
    }

    private void addPolymorphicChild(MetaModel metaModel, Model child){
        OneToManyPolymorphicAssociation ass = metaModel.getAssociationForTarget(child.getClass(), OneToManyPolymorphicAssociation.class);
        child.set("parent_id", getId());
        child.set("parent_type", ass.getTypeLabel());
        child.saveIt();
    }

    private void addOne2ManyChild(MetaModel metaModel, Model child){
        OneToManyAssociation ass = metaModel.getAssociationForTarget(child.getClass(), OneToManyAssociation.class);
        String fkName = ass.getFkName();
        child.set(fkName, getId());
        child.saveIt();//this will cause an exception in case validations fail.
    }

    private void addMany2ManyChild(MetaModel metaModel, Model child, MetaModel childMetaModel){
        Many2ManyAssociation ass = metaModel.getAssociationForTarget(child.getClass(), Many2ManyAssociation.class);
        if (child.getId() == null) {
            child.saveIt();
        }
        MetaModel joinMetaModel = metaModelFor(ass.getJoin());
        if (joinMetaModel == null) {
            new DB(metaModel.getDbName()).exec(metaModel.getDialect().insertManyToManyAssociation(ass),
                    getId(), child.getId());
        } else {
            //this is for Oracle, many 2 many, and all annotations used, including @IdGenerator. In this case,
            //it is best to delegate generation of insert to a model (sequences, etc.)
            try {
                Model joinModel = joinMetaModel.getModelClass().getDeclaredConstructor().newInstance();
                joinModel.set(ass.getSourceFkName(), getId());
                joinModel.set(ass.getTargetFkName(), child.getId());
                joinModel.saveIt();
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                throw new InitException("failed to create a new instance of class: " + joinMetaModel.getClass()
                        + ", are you sure this class has a default constructor?", e);
            } catch (IllegalAccessException e) {
                throw new InitException(e);
            } finally {
                Registry.cacheManager().purgeTableCache(ass.getJoin());
                Registry.cacheManager().purgeTableCache(metaModel);
                Registry.cacheManager().purgeTableCache(childMetaModel);
            }
        }
    }

    /**
     * Convenience method. Calls {@link #add(Model)} one at the time for each member of the list.
     * All rules of the {@link #add(Model)} method apply.
     *
     * @param models list of model instances to add to this one.
     */
    public <T extends Model>  void addModels(List<T> models){
        for (T model : models) {
            add(model);
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
     * <p></p>
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

            MetaModel metaModel = metaModelLocal;
            if (metaModel.hasAssociation(child.getClass(), OneToManyAssociation.class)
                    || metaModel.hasAssociation(child.getClass(), OneToManyPolymorphicAssociation.class)) {
                child.delete();
                return 1;
            } else if (metaModel.hasAssociation(child.getClass(), Many2ManyAssociation.class)) {
                return new DB(metaModel.getDbName()).exec(metaModel.getDialect().deleteManyToManyAssociation(
                        metaModel.getAssociationForTarget(child.getClass(), Many2ManyAssociation.class)),
                        getId(), child.getId());
            } else {
                throw new NotAssociatedException(getClass(), child.getClass());
            }
        } else {
            throw new IllegalArgumentException("You can only add associated model to an instance that exists in DB. "
                    + "Save this instance first, then you will be able to add dependencies to it.");
        }
    }

    /**
     * This method will not exit silently like {@link #save()}, it instead will throw {@link ValidationException}
     * if validations did not pass.
     *
     * @return  true if the model was saved, false if you set an ID value for the model, but such ID does not exist in DB.
     */
    public boolean saveIt(String fields) {
        boolean result = save(fields);
        ModelDelegate.purgeEdges(metaModelLocal);
        if (!errors.isEmpty()) {
            throw new ValidationException(this);
        }
        return result;
    }
	
    /**
     * This method will not exit silently like {@link #save()}, it instead will throw {@link org.javalite.activejdbc.validation.ValidationException}
     * if validations did not pass.
     *
     * @param fields String representing a list of attributes that will be updated
     * @return  true if the model was saved, false if you set an ID value for the model, but such ID does not exist in DB.
     */
	public boolean saveIt() {return saveIt("");}


    /**
     * Resets all data in this model, including the ID.
     * After this method, this instance is equivalent to an empty, just created instance.
     */
    public void reset() {
        attributes = new CaseInsensitiveMap<>();
    }

    /**
     * Unfreezes this model. After this method it is possible again to call save() and saveIt() methods.
     * This method will erase the value of ID on this instance, while preserving all other attributes' values.
     *
     * If a record was deleted, it is frozen and cannot be saved. After it is thawed, it can be saved again, but it will
     * generate a new insert statement and create a new record in the table with all the same attribute values.
     *
     * <p><p/>
     * Synonym for {@link #defrost()}.
     */
    public void thaw(){
        attributes.put(getIdName(), null);
        compositeKeyPersisted = false;
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
    public boolean save() {return save("");}

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
     * @param fields String representing a list of attributes that will be updated
     * @return true if a model was saved and false if values did not pass validations and the record was not saved.
     * False will also be returned if you set an ID value for the model, but such ID does not exist in DB.
     */	
    public boolean save(String fields) {
        if(frozen) throw new FrozenException(this);

        fireBeforeSave();

	if (fields == null || fields.isEmpty()){
        validate();
        if (hasErrors()) {
            return false;
        }
		}

        boolean result;
        if (getId() == null && !compositeKeyPersisted) {
            result = insert();
        } else {
            result = update(fields);
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
     * new record or a one that exists in the table. It does not have the "belt and suspenders" protections. It will
     * simply generate and execute an insert statement, assuming that developer knows what he/she is doing.
     *
     * <p></p>
     * Additionally, this method will set the <code>updated_at</code> and <code>created_at</code> attributes on this instance to current time.
     * In case the insertion fails, these attributes will stay changed anyway.
     *
     * @return true if model was saved, false if not
     */
    public boolean insert() {

        fireBeforeCreate();

        doCreatedAt();
        doUpdatedAt();

        MetaModel metaModel = metaModelLocal;
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() != null && !metaModel.getVersionColumn().equals(entry.getKey())) {
                columns.add(entry.getKey());
                values.add(entry.getValue());
            }
        }
        if (metaModel.isVersioned()) {
            columns.add(metaModel.getVersionColumn());
            values.add(1);
        }

        try {
            boolean containsId = (attributes.get(metaModel.getIdName()) != null); // do not use containsKey
            boolean done;
            String query = metaModel.getDialect().insertParametrized(metaModel, columns, containsId);
            if (containsId || getCompositeKeys() != null) {
                compositeKeyPersisted  = done = (1 == new DB(metaModel.getDbName()).exec(query, values.toArray()));
            } else {
                Object id = new DB(metaModel.getDbName()).execInsert(query, metaModel.getIdName(), values.toArray());
                attributes.put(metaModel.getIdName(), id);
                done = (id != null);
            }
            if (metaModel.cached()) {
                Registry.cacheManager().purgeTableCache(metaModel);
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
        if (manageTime && metaModelLocal.hasAttribute("created_at")) {
            attributes.put("created_at", new Timestamp(System.currentTimeMillis()));
        }
    }

    private void doUpdatedAt() {
        if (manageTime && metaModelLocal.hasAttribute("updated_at")) {
            attributes.put("updated_at", new Timestamp(System.currentTimeMillis()));
        }
    }

    private boolean update(String fields) {

        fireBeforeUpdate();
        doUpdatedAt();

        MetaModel metaModel = metaModelLocal;
        StringBuilder query = new StringBuilder().append("UPDATE ").append(metaModel.getTableName()).append(" SET ");
        Set<String> attributeNames = metaModel.getAttributeNamesSkipGenerated(manageTime);
        attributeNames.retainAll(dirtyAttributeNames);
        
        if (fields != null || !fields.trim().isEmpty())			
	{
	String[] listFields = fields.split(",");

	for( int bb=0; bb<listFields.length-1; bb++){
		if (!attributeNames.contains(listFields[bb].replaceAll("^\\s+","").replaceAll("\\s+$",""))){
                 throw new StaleModelException("Failed to update record for model" 
                         +getClass()
                         +" not found column "
                         +listFields[bb].replaceAll("^\\s+","").replaceAll("\\s+$",""));
                }
	}            
            
	attributeNames.clear();	
	for( int aa=0; aa<listFields.length-1; aa++){
		attributeNames.add(listFields[aa].replaceAll("^\\s+","").replaceAll("\\s+$",""));
	}
	}
        
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
            query.append(metaModelLocal.getVersionColumn()).append(" = ?");
            values.add(getLong(metaModelLocal.getVersionColumn()) + 1);
        }
        if(values.isEmpty())
            return false;

        if (getCompositeKeys() != null) {
            String[] compositeKeys = getCompositeKeys();
            for (int i = 0; i < compositeKeys.length; i++) {
                query.append(i == 0 ? " WHERE " : " AND ").append(compositeKeys[i]).append(" = ?");
                values.add(get(compositeKeys[i]));
            }
        } else {
            query.append(" WHERE ").append(metaModel.getIdName()).append(" = ?");
            values.add(getId());
            if(metaModel.hasPartitionIDs()){
                for (String partitionId : metaModel.getPartitionIDs()) {
                    query.append(" AND ").append(partitionId).append(" = ?");
                    values.add(get(partitionId));
                }
            }
        }
        if (metaModel.isVersioned()) {
            query.append(" AND ").append(metaModelLocal.getVersionColumn()).append(" = ?");
            values.add(get(metaModelLocal.getVersionColumn()));
        }
        int updated = new DB(metaModel.getDbName()).exec(query.toString(), values.toArray());
        if(metaModel.isVersioned() && updated == 0){
            throw new StaleModelException("Failed to update record for model '" + getClass() +
                    "', with " + getIdName() + " = " + getId() + " and " + metaModelLocal.getVersionColumn()
                    + " = " + get(metaModelLocal.getVersionColumn()) +
                    ". Either this record does not exist anymore, or has been updated to have another "
                    + metaModelLocal.getVersionColumn() + '.');
        }else if(metaModel.isVersioned()){
            set(metaModelLocal.getVersionColumn(), getLong(metaModelLocal.getVersionColumn()) + 1);
        }
        if(metaModel.cached()){
            Registry.cacheManager().purgeTableCache(metaModel);
        }
        dirtyAttributeNames.clear();
        fireAfterUpdate();
        return updated > 0;
    }

    private List<Object> getAttributeValues(Set<String> attributeNames) {
        List<Object> values = new ArrayList<>();
        for (String attribute : attributeNames) {
            values.add(get(attribute));
        }
        return values;
    }

    private static <T extends Model> Class<T> modelClass() {
        throw new InitException("failed to determine Model class name, are you sure models have been instrumented?");
    }

    /**
     * Returns name of corresponding table.
     *
     * @return name of corresponding table.
     */
    public static String getTableName() {
        return ModelDelegate.tableNameOf(modelClass());
    }

    /**
     * Value of ID.
     *
     * @see #getLongId()
     *
     * @return of ID.
     */
    public Object getId() {
        return get(getIdName());
    }

    /**
     * Name of ID column.
     *
     * @return Name of ID column.
     */
    public String getIdName() {
        return metaModelLocal.getIdName();
    }

    /**
     * Provides a list of composite keys as specified  in {@link CompositePK}.
     *
     * @return a list of composite keys as specified  in {@link CompositePK}.
     */
    public String[] getCompositeKeys() {
        return metaModelLocal.getCompositeKeys();
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
     * @param replacements an array of strings, where odd values are to be replaced in the values of the attributes
     *                     and even values are replacements. For instance, your value is "O'Donnel", which contains
     *                     a single quote. In order to escape/replace it, you can:
     *
     *                     <code>person.toInsert(dialect, "'", "''")</code>, which will escape a single quote by two
     *                     single quotes.
     *
     * @return INSERT SQL based on this model.
     */
    public String toInsert(String ... replacements) {
        return toInsert(metaModelLocal.getDialect(), replacements);
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
     * @param replacements an array of strings, where odd values are to be replaced in the values of the attributes
     *                     and even values are replacements. For instance, your value is "O'Donnel", which contains
     *                     a single quote. In order to escape/replace it, you can:
     *
     *                     <code>person.toUpdate(dialect, "'", "''")</code>, which will escape a single quote by two
     *                     single quotes.
     * @return INSERT SQL based on this model.
     */
    public String toInsert(Dialect dialect, String ... replacements) {
        return dialect.insert(metaModelLocal, attributes, replacements);
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
     * @param replacements an array of strings, where odd values are to be replaced in the values of the attributes
     *                     and even values are replacements. For instance, your value is "O'Donnel", which contains
     *                     a single quote. In order to escape/replace it, you can:
     *
     *                     <code>person.toUpdate(dialect, "'", "''")</code>, which will escape a single quote by two
     *                     single quotes.
     *
     * @return UPDATE SQL based on this model.
     */
    public String toUpdate(String ... replacements) {
        return toUpdate(metaModelLocal.getDialect(), replacements);
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
     * @param replacements an array of strings, where odd values are to be replaced in the values of the attributes
     *                     and even values are replacements. For instance, your value is "O'Donnel", which contains
     *                     a single quote. In order to escape/replace it, you can:
     *
     *                     <code>person.toUpdate(dialect, "'", "''")</code>, which will escape a single quote by two
     *                     single quotes.
     *
     *
     * @return UPDATE SQL based on this model.
     */
    public String toUpdate(Dialect dialect, String ... replacements) {
        return dialect.update(metaModelLocal, attributes, replacements);
    }

    /**
     * @return true if this models has a {@link Cached} annotation.
     */
    public static boolean isCached(){
        return modelClass().getAnnotation(Cached.class) != null;
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
        return getId() == null ? null: Convert.toLong(getId());
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(attributes);
        out.writeObject(dirtyAttributeNames);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        attributes = (Map<String, Object>) in.readObject();
        dirtyAttributeNames = (Set<String>) in.readObject();
    }


}
