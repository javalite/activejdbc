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

import org.h2.util.StringUtils;
import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.DbName;
import org.javalite.activejdbc.annotations.IdGenerator;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.VersionColumn;
import org.javalite.activejdbc.cache.CacheManager;
import org.javalite.activejdbc.dialects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;

import org.javalite.common.Collections;
import org.javalite.common.Convert;

import static org.javalite.common.Util.*;

/**
 * @author Igor Polevoy
 */
public class Configuration {

    public static class ModelAttributes {
    	private String tableName;
    	private String idColumn;
    	private String idGeneratorCode;
    	private String versionColumn;
    	private String dbName = DB.DEFAULT_NAME;
    	private Boolean cached;
    	
    	public ModelAttributes() {
    		
    	}
    	
    	
    	
    	public ModelAttributes(String dbName, String tableName, Boolean cached,
				String idColumn, String idGeneratorCode, String versionColumn) {
			this.dbName = dbName;
			this.tableName = tableName;
			this.cached = cached;
			this.idColumn = idColumn;
			this.idGeneratorCode = idGeneratorCode;
			this.versionColumn = versionColumn;
		}

    	public ModelAttributes(String dbName) {
			this.dbName = dbName;
		}

    	
		public ModelAttributes(String dbName, String tableName) {
			this.dbName = dbName;
			this.tableName = tableName;
		}
		
		public ModelAttributes(String dbName, String tableName, Boolean cached) {
			this.dbName = dbName;
			this.tableName = tableName;
			this.cached = cached;
		}
		
		public ModelAttributes(String dbName, String tableName, Boolean cached,
				String idColumn) {
			this.dbName = dbName;
			this.tableName = tableName;
			this.cached = cached;
			this.idColumn = idColumn;
		}

		public String getTableName() {
			return tableName;
		}
		public String getIdColumn() {
			return idColumn;
		}
		public String getIdGeneratorCode() {
			return idGeneratorCode;
		}
		public String getVersionColumn() {
			return versionColumn;
		}
		public String getDbName() {
			return dbName;
		}
		public Boolean getCached() {
			return cached;
		}
    	
    	
    }
	//key is a DB name, value is a list of model names
    private Map<String, Collection<String>> modelsMap = new HashMap<String, Collection<String>>();
    private Set<String> modelNameSet = new HashSet<String>();
    private Properties properties = new Properties();
    private static CacheManager cacheManager;
    private final static Logger logger = LoggerFactory.getLogger(Configuration.class);
    

    private Map<String, Dialect> dialects = new CaseInsensitiveMap<Dialect>();
    
    public void addModel(String modelName) {
    	addModel(modelName,new ModelAttributes());
    }
    
    public void addModel(String modelName,ModelAttributes modelAttributes) {
    	modelNameSet.add(modelName);
    	addModelToDB(modelName,modelAttributes.dbName);
    	loadModelClass(modelName, modelAttributes);
    }
    
    private void addModelToDB(String modelName,String dbName) {
    	Collection<String> dbModels = null;
    	if(!modelsMap.containsKey(dbName)) {
    		dbModels = new HashSet<String>();
    		modelsMap.put(dbName,dbModels);
    	} else {
    		dbModels = modelsMap.get(dbName);
    	}
    	
    	dbModels.add(modelName);
    }
    
    protected Configuration(){
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("activejdbc_models.properties");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                LogFilter.log(logger, "Load models from: {}", url.toExternalForm());
                InputStream inputStream = null;
                InputStreamReader isreader = null;
                BufferedReader reader = null;
                try {
                    inputStream = url.openStream();
                    isreader = new InputStreamReader(inputStream);
                    reader = new BufferedReader(isreader);
                    String line;
                    while ((line = reader.readLine()) != null) {

                        String[] parts = split(line, ':');
                        String modelName = parts[0];
                        String dbName = parts[1];

                        Collection<String> modelNames = modelsMap.get(dbName);
                        if (modelNames == null) {
                            modelNames = new ArrayList<String>();
                            modelsMap.put(dbName, modelNames);
                        }
                        modelNames.add(modelName);
                    }
                } finally {
                    closeQuietly(reader);
                    closeQuietly(isreader);
                    closeQuietly(inputStream);
                }
            }
        } catch (IOException e) {
            throw new InitException(e);
        }
        if(modelsMap.isEmpty()){
            LogFilter.log(logger, "ActiveJDBC Warning: Cannot locate any models, assuming project without models.");
            return;
        }
        try {
            InputStream in = getClass().getResourceAsStream("/activejdbc.properties");
            if (in != null) { properties.load(in); }
        } catch (IOException e){
            throw new InitException(e);
        }

        String cacheManagerClass = properties.getProperty("cache.manager");
        if(cacheManagerClass != null){

            try{
                Class cmc = Class.forName(cacheManagerClass);
                cacheManager = (CacheManager)cmc.newInstance();
            } catch(Exception e) {
                throw new InitException("failed to initialize a CacheManager. Please, ensure that the property " +
                        "'cache.manager' points to correct class which extends 'org.javalite.activejdbc.cache.CacheManager' class and provides a default constructor.", e);
            }

        }
    }

    Collection<String> getModelNames(String dbName) throws IOException {
        return modelsMap.get(dbName);
    }

    public boolean collectStatistics() {
        return Convert.toBoolean(properties.getProperty("collectStatistics", "false"));
    }

    public boolean collectStatisticsOnHold() {
        return Convert.toBoolean(properties.getProperty("collectStatisticsOnHold", "false"));
    }

    public boolean cacheEnabled(){
        return cacheManager != null;
    }

    Dialect getDialect(MetaModel mm){
        Dialect dialect = dialects.get(mm.getDbType());
        if (dialect == null) {
            if(mm.getDbType().equalsIgnoreCase("Oracle")){
                dialect = new OracleDialect();
            }
            else if(mm.getDbType().equalsIgnoreCase("MySQL")){
                dialect = new MySQLDialect();
            }
            else if(mm.getDbType().equalsIgnoreCase("PostgreSQL")){
                dialect = new PostgreSQLDialect();
            }
            else if(mm.getDbType().equalsIgnoreCase("h2")){
                dialect = new H2Dialect();
            }
            else if(mm.getDbType().equalsIgnoreCase("Microsoft SQL Server")){
                dialect = new MSSQLDialect();
            }
            else if(mm.getDbType().equalsIgnoreCase("SQLite")){
                dialect = new SQLiteDialect();
            }else{
                dialect = new DefaultDialect();
            }
            dialects.put(mm.getDbType(), dialect);
        }
        return dialect;
    }

    
    public CacheManager getCacheManager(){
        return cacheManager;
    }
    
    private static Class<? extends Model> loadModelClass(String className,Configuration.ModelAttributes attribs) {
    	try {
			return (Class<? extends Model>) Class.forName(className);
    	} catch (ClassNotFoundException ignore) {
		}
    	
     	
    	// Create the class.
    	try {
    		ClassPool pool = ClassPool.getDefault();

    		// Create the class.
    		CtClass subClass = pool.makeClass(className);
    		
    		//***set annotations
    		ConstPool constPool = subClass.getClassFile().getConstPool();
    		AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
    		subClass.getClassFile().addAttribute(attr);
    		Annotation anno = null;
    		
    		//db name annotation
    		if(!StringUtils.isNullOrEmpty(attribs.dbName)) {
	    		anno = new Annotation(constPool, pool.get(DbName.class.getName()));
	    		anno.addMemberValue("value", new StringMemberValue(attribs.dbName, constPool));
	    		attr.setAnnotation(anno);
    		}
    		
    		//table name annotation
    		if(!StringUtils.isNullOrEmpty(attribs.tableName)) {
	    		anno = new Annotation(constPool, pool.get(Table.class.getName()));
	    		anno.addMemberValue("value", new StringMemberValue(attribs.tableName, constPool));
	    		attr.setAnnotation(anno);
    		}
    		
    		//id column annotation
    		if(!StringUtils.isNullOrEmpty(attribs.idColumn)) {
	    		anno = new Annotation(constPool, pool.get(IdName.class.getName()));
	    		anno.addMemberValue("value", new StringMemberValue(attribs.idColumn, constPool));
	    		attr.setAnnotation(anno);
    		}
    		
    		//id generator code annotation
    		if(!StringUtils.isNullOrEmpty(attribs.idGeneratorCode)) {
	    		anno = new Annotation(constPool, pool.get(IdGenerator.class.getName()));
	    		anno.addMemberValue("value", new StringMemberValue(attribs.idGeneratorCode, constPool));
	    		attr.setAnnotation(anno);
    		}
    		
    		//version column annotation
    		if(!StringUtils.isNullOrEmpty(attribs.versionColumn)) {
	    		anno = new Annotation(constPool, pool.get(VersionColumn.class.getName()));
	    		anno.addMemberValue("value", new StringMemberValue(attribs.versionColumn, constPool));
	    		attr.setAnnotation(anno);
    		}
    		
    		//cached annotation
    		if(attribs.cached != null && attribs.cached == true) {
	    		anno = new Annotation(constPool, pool.get(Cached.class.getName()));
	    		attr.setAnnotation(anno);
    		}
    		
    		
    		//set super class
    		final CtClass superClass = pool.get(Model.class.getName());
    		subClass.setSuperclass(superClass);
    		subClass.setModifiers( Modifier.PUBLIC );
    		
    		CtConstructor constr = CtNewConstructor.defaultConstructor(subClass);
    		constr.setModifiers(Modifier.PUBLIC);
    		subClass.addConstructor(constr);
    		
    		 
			return subClass.toClass();
    	} catch (CannotCompileException ex) {
    		throw new RuntimeException(ex);
    	} catch (NotFoundException ex) {
    		throw new RuntimeException(ex);
    	}
    }

}