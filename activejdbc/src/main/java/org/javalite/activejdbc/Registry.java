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

import org.javalite.activejdbc.annotations.*;
import org.javalite.activejdbc.associations.*;
import org.javalite.activejdbc.cache.CacheManager;
import org.javalite.activejdbc.cache.QueryCache;
import org.javalite.activejdbc.statistics.StatisticsQueue;

import java.lang.reflect.Method;
import java.sql.DatabaseMetaData;
import java.util.*;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.ResultSet;

import org.javalite.common.Inflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Igor Polevoy
 * @author Eric Nielsen
 */
public enum Registry {

    //our singleton!
    INSTANCE;

    private final static Logger logger = LoggerFactory.getLogger(Registry.class);

    private final MetaModels metaModels = new MetaModels();
    private final Map<Class, ModelRegistry> modelRegistries = new HashMap<Class, ModelRegistry>();
    private final Configuration configuration = new Configuration();
    private final StatisticsQueue statisticsQueue;
    private final Set<String> initedDbs = new HashSet<String>();

    private Registry() {
        statisticsQueue = configuration.collectStatistics()
                ? new StatisticsQueue(configuration.collectStatisticsOnHold())
                : null;
    }

    /**
     * @deprecated Not used anymore.
     */
    @Deprecated
    public boolean initialized() {
        // This will return true if AT LEAST ONE DB was initialized, not if ALL were (if there are more than one).
        // Hopefully this is not used anywhere.
        return !initedDbs.isEmpty();
    }

    public static Registry instance() {
        return INSTANCE;
    }

    public StatisticsQueue getStatisticsQueue() {
        if (statisticsQueue == null) {
            throw new InitException("cannot collect statistics if this was not configured in activejdbc.properties file. Add 'collectStatistics = true' to it.");
        }
        return statisticsQueue;
    }

    public Configuration getConfiguration(){
        return configuration;
    }

    public static CacheManager cacheManager(){
        return QueryCache.instance().getCacheManager();
    }

    /**
     * Provides a MetaModel of a model representing a table.
     *
     * @param table name of table represented by this MetaModel.
     * @return MetaModel of a model representing a table.
     */
    public MetaModel getMetaModel(String table) {
        return metaModels.getMetaModel(table);
    }

    public MetaModel getMetaModel(Class<? extends Model> modelClass) {

        String dbName = MetaModel.getDbName(modelClass);
        init(dbName);

        return metaModels.getMetaModel(modelClass);
    }

    ModelRegistry modelRegistryOf(Class<? extends Model> modelClass) {
        ModelRegistry registry = modelRegistries.get(modelClass);
        if (registry == null) {
            registry = new ModelRegistry();
            modelRegistries.put(modelClass, registry);
        }
        return registry;
    }

     synchronized void init(String dbName) {

        if (initedDbs.contains(dbName)) {
            return;
        } else {
            initedDbs.add(dbName);
            ModelFinder.unregisterModelClasses(dbName);
        }

        try {
            ModelFinder.findModels(dbName);
            Connection c = ConnectionsAccess.getConnection(dbName);
            if(c == null){
                throw new DBException("Failed to retrieve metadata from DB, connection: '" + dbName + "' is not available");
            }
            DatabaseMetaData databaseMetaData = c.getMetaData();
            //TODO: this is called databaseProductName, dbType or dbProduct throughtout the code; use unique name?
            String databaseProductName = c.getMetaData().getDatabaseProductName();
            Collection<Class<? extends Model>> modelClasses = ModelFinder.getModelsForDb(dbName);
            registerModels(dbName, modelClasses, databaseProductName);
            String[] tables = metaModels.getTableNames(dbName);

            for (String table : tables) {
                Map<String, ColumnMetadata> metaParams = fetchMetaParams(databaseMetaData, databaseProductName, table);
                registerColumnMetadata(table, metaParams);
            }

            processOverrides(modelClasses);

            for (String table : tables) {
                discoverAssociationsFor(table, dbName);
            }
        } catch (Exception e) {
            initedDbs.remove(dbName);
            if (e instanceof InitException) {
                throw (InitException) e;
            }
            if (e instanceof DBException) {
                throw (DBException) e;
            } else {
                throw new InitException(e);
            }
        }
    }

    /**
     * Returns a hash keyed off a column name.
     *
     * @return
     * @throws java.sql.SQLException
     */
    private Map<String, ColumnMetadata> fetchMetaParams(DatabaseMetaData databaseMetaData, String databaseProductName, String table) throws SQLException {

        /*
         * Valid table name format: tablename or schemaname.tablename
         */
        String[] names = table.split("\\.", 3);
        String schema = null;
        String tableName;
        switch (names.length) {
        case 1:
            tableName = names[0];
            break;
        case 2:
            schema = names[0];
            tableName = names[1];
            if (schema.isEmpty() || tableName.isEmpty()) {
                throw new DBException("invalid table name : " + table);
            }
            break;
        default:
            throw new DBException("invalid table name: " + table);
        }

        ResultSet rs = databaseMetaData.getColumns(null, schema, tableName, null);
        String dbProduct = databaseMetaData.getDatabaseProductName().toLowerCase();
        Map<String, ColumnMetadata> columns = getColumns(rs, dbProduct);
        rs.close();

        //try upper case table name - Oracle uses upper case
        if (columns.isEmpty()) {
            rs = databaseMetaData.getColumns(null, schema, tableName.toUpperCase(), null);
            dbProduct = databaseProductName.toLowerCase();
            columns = getColumns(rs, dbProduct);
            rs.close();
        }

        //if upper case not found, try lower case.
        if (columns.isEmpty()) {
            rs = databaseMetaData.getColumns(null, schema, tableName.toLowerCase(), null);
            columns = getColumns(rs, dbProduct);
            rs.close();
        }

        if(columns.size() > 0){
            LogFilter.log(logger, "Fetched metadata for table: {}", table);
        }
        else{
            logger.warn("Failed to retrieve metadata for table: '{}'."
                    + " Are you sure this table exists? For some databases table names are case sensitive.",
                    table);
        }
        return columns;
    }


    /**
     *
     * @param modelClasses
     * @param dbType this is a name of a DBMS as returned by JDBC driver, such as Oracle, MySQL, etc.
     */
    private void registerModels(String dbName, Collection<Class<? extends Model>> modelClasses, String dbType) {
        for (Class<? extends Model> modelClass : modelClasses) {
            MetaModel mm = new MetaModel(dbName, modelClass, dbType);
            metaModels.addMetaModel(mm, modelClass);
            LogFilter.log(logger, "Registered model: {}", modelClass);
        }
    }

    private void processOverrides(Collection<Class<? extends Model>> models) {

        for(Class<? extends Model> modelClass : models){

            BelongsTo belongsToAnnotation = modelClass.getAnnotation(BelongsTo.class);
            processOverridesBelongsTo(modelClass, belongsToAnnotation);

            BelongsToParents belongsToParentAnnotation = modelClass.getAnnotation(BelongsToParents.class);
            if (belongsToParentAnnotation != null)
            	for (BelongsTo belongsTo : belongsToParentAnnotation.value())
            		processOverridesBelongsTo(modelClass, belongsTo);

            Many2Many many2manyAnnotation = modelClass.getAnnotation(Many2Many.class);

            if(many2manyAnnotation != null){

                Class<? extends Model> otherClass = many2manyAnnotation.other();

                String source = getTableName(modelClass);
                String target = getTableName(otherClass);
                String join = many2manyAnnotation.join();
                String sourceFKName = many2manyAnnotation.sourceFKName();
                String targetFKName = many2manyAnnotation.targetFKName();
                String otherPk;
                String thisPk;
                try {
                    Method m = modelClass.getMethod("getMetaModel");
                    MetaModel mm = (MetaModel) m.invoke(modelClass);
                    thisPk = mm.getIdName();
                    m = otherClass.getMethod("getMetaModel");
                    mm = (MetaModel) m.invoke(otherClass);
                    otherPk = mm.getIdName();
                } catch (Exception e) {
                    throw new InitException("failed to determine PK name in many to many relationship", e);
                }

                Association many2many1 = new Many2ManyAssociation(source, target, join, sourceFKName, targetFKName, otherPk);
                metaModels.getMetaModel(source).addAssociation(many2many1);

                Association many2many2 = new Many2ManyAssociation(target, source, join, targetFKName, sourceFKName, thisPk);
                metaModels.getMetaModel(target).addAssociation(many2many2);
            }

            BelongsToPolymorphic belongsToPolymorphic = modelClass.getAnnotation(BelongsToPolymorphic.class);
            if(belongsToPolymorphic != null){

                Class<? extends Model>[] parentClasses = belongsToPolymorphic.parents();
                String[] typeLabels = belongsToPolymorphic.typeLabels();

                if(typeLabels.length > 0 && typeLabels.length != parentClasses.length){
                    throw new InitException("must provide all type labels for polymorphic associations");
                }

                for (int i = 0, parentClassesLength = parentClasses.length; i < parentClassesLength; i++) {
                    Class<? extends Model> parentClass = parentClasses[i];

                    String typeLabel = typeLabels.length > 0? typeLabels[i]: parentClass.getName();

                    BelongsToPolymorphicAssociation belongsToPolymorphicAssociation =
                            new BelongsToPolymorphicAssociation(getTableName(modelClass), getTableName(parentClass), typeLabel, parentClass.getName());
                    metaModels.getMetaModel(modelClass).addAssociation(belongsToPolymorphicAssociation);


                    OneToManyPolymorphicAssociation oneToManyPolymorphicAssociation =
                            new OneToManyPolymorphicAssociation(getTableName(parentClass), getTableName(modelClass), typeLabel);
                    metaModels.getMetaModel(parentClass).addAssociation(oneToManyPolymorphicAssociation);
                }
            }
        }
    }

    private void processOverridesBelongsTo(Class<? extends Model> modelClass, BelongsTo belongsToAnnotation) {
        if(belongsToAnnotation != null){
            Class<? extends Model> parentClass = belongsToAnnotation.parent();
            String foreignKeyName = belongsToAnnotation.foreignKeyName();
            Association hasMany = new OneToManyAssociation(getTableName(parentClass), getTableName(modelClass), foreignKeyName);
            Association belongsTo = new BelongsToAssociation(getTableName(modelClass), getTableName(parentClass), foreignKeyName);

            metaModels.getMetaModel(parentClass).addAssociation(hasMany);
            metaModels.getMetaModel(modelClass).addAssociation(belongsTo);
        }
	}


    private Map<String, ColumnMetadata> getColumns(ResultSet rs, String dbProduct) throws SQLException {
        Map<String, ColumnMetadata> columns = new CaseInsensitiveMap<ColumnMetadata>();
        while (rs.next()) {

        	if ("h2".equals(dbProduct) && "INFORMATION_SCHEMA".equals(rs.getString("TABLE_SCHEM"))) {
                continue; // skip h2 INFORMATION_SCHEMA table columns.
            }
            ColumnMetadata cm = new ColumnMetadata(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"));
            columns.put(cm.getColumnName(), cm);
        }
        return columns;
    }

    private void discoverAssociationsFor(String source, String dbName) {
        discoverOne2ManyAssociationsFor(source, dbName);
        discoverMany2ManyAssociationsFor(source, dbName);
    }

    private void discoverMany2ManyAssociationsFor(String source, String dbName) {
        for (String join : metaModels.getTableNames(dbName)) {
            String other = Inflector.getOtherName(source, join);
            if (other == null || getMetaModel(other) == null || !hasForeignKeys(join, source, other))
                continue;

            Association associationSource = new Many2ManyAssociation(source, other, join,
                    getMetaModel(source).getFKName(), getMetaModel(other).getFKName());
            getMetaModel(source).addAssociation(associationSource);
        }
    }

    /**
     * Checks that the "join" table has foreign keys from "source" and "other" tables. Returns true
     * if "join" table exists and contains foreign keys of "source" and "other" tables, false otherwise.
     *
     * @param join   - potential name of a join table.
     * @param source name of a "source" table
     * @param other  name of "other" table.
     * @return true if "join" table exists and contains foreign keys of "source" and "other" tables, false otherwise.
     */
    private boolean hasForeignKeys(String join, String source, String other) {
        String sourceFKName = getMetaModel(source).getFKName();
        String otherFKName = getMetaModel(other).getFKName();
        MetaModel joinMM = getMetaModel(join);
        return joinMM.hasAttribute(sourceFKName) && joinMM.hasAttribute(otherFKName);
    }


    /**
     * Discover many to many associations.
     *
     * @param source name of table for which associations are searched.
     */
    private void discoverOne2ManyAssociationsFor(String source, String dbName) {

        MetaModel sourceMM = getMetaModel(source);

        for (String target : metaModels.getTableNames(dbName)) {

            MetaModel targetMM = getMetaModel(target);

            String sourceFKName = getMetaModel(source).getFKName();
            if (targetMM != sourceMM && targetMM.hasAttribute(sourceFKName)) {
                targetMM.addAssociation(new BelongsToAssociation(target, source, sourceFKName));
                sourceMM.addAssociation(new OneToManyAssociation(source, target, sourceFKName));
            }
        }
    }



    /**
     * Returns model class for a table name, null if not found.
     *
     * @param table table name
     * @param suppressException true to suppress exception
     * @return model class for a table name, null if not found.s
     */
    protected Class<? extends Model> getModelClass(String table, boolean suppressException) {
        Class modelClass = metaModels.getModelClass(table);

        if(modelClass == null && !suppressException)
            throw new InitException("failed to locate meta model for: " + table + ", are you sure this is correct table name?");

        return modelClass;
    }

    protected String getTableName(Class<? extends Model> modelClass) {

        init(MetaModel.getDbName(modelClass));
        String tableName = metaModels.getTableName(modelClass);
        if (tableName == null) {
            throw new DBException("failed to find metamodel for " + modelClass + ". Are you sure that a corresponding table  exists in DB?");
        }
        return tableName;
    }

    /**
     * Returns edges for a join. An edge is a table in a many to many relationship that is not a join.
     *
     * We have to go through all the associations here because join tables, (even if the model exists) will not
     * have associations to the edges.
     *
     * @param join name of join table;
     * @return edges for a join
     */
    protected List<String> getEdges(String join) {
        return metaModels.getEdges(join);
    }

    private void registerColumnMetadata(String table, Map<String, ColumnMetadata> metaParams) {
        metaModels.setColumnMetadata(table, metaParams);
    }
    
    synchronized public void resetInitializedFlag(String dbName) {
    	initedDbs.remove(dbName);
    }
}
