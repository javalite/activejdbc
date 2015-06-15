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


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;

import org.h2.util.StringUtils;
import org.javalite.activejdbc.annotations.DbName;
import org.javalite.activejdbc.annotations.IdGenerator;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.VersionColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelFinder {

    //this is a map of lists of model classes. Keys are names of databases as specified in the models' @DbName annotations.
    private static final Map<String, Collection<Class<? extends Model>>> modelClasses = new HashMap<String, Collection<Class<? extends Model>>>();

    private static final List<String> modelClassNames = new ArrayList<String>();
    private final static Logger logger = LoggerFactory.getLogger(Configuration.class);

    protected static void findModels(String dbName) throws IOException, ClassNotFoundException {
        //this is for static instrumentation. In case of dynamic, the  modelClassNames will already be filled.
        Collection<String> models = Registry.instance().getConfiguration().getModelNames(dbName);
        if (models != null && !models.isEmpty()) {
            for (String model : models) {
                modelFound(model);
            }
        } else {
            throw new InitException("you are trying to work with models, but no models are found. Maybe you have " +
                    "no models in project, or you did not instrument the models. It is expected that you have " +
                    "a file activejdbc_models.properties on classpath");
        }
    }

    protected static Collection<Class<? extends Model>> getModelsForDb(String dbName) throws IOException, ClassNotFoundException {
        synchronized (modelClasses){
            if(!modelClasses.containsKey(dbName)){
            	
            	Collection<String> classNames = Registry.instance().getConfiguration().getModelNames(dbName);
            	if(classNames != null && classNames.size() > 0) {
            		registerModelClasses(dbName);
            	} else {
            		for (String className : modelClassNames) {
                        registerModelClass(className);
                    }
            	}
            }
            return modelClasses.get(dbName);
        }
    }

    //called dynamically from JavaAgent
    public static void modelFound(String modelClassName){
        synchronized (modelClassNames){
            if(!modelClassNames.contains(modelClassName))
                modelClassNames.add(modelClassName);
        }
    }

    @SuppressWarnings("unchecked")
    public static void registerModelClass(String className) throws IOException, ClassNotFoundException {
        Class clazz = Class.forName(className);
        if (Model.class.isAssignableFrom(clazz) && clazz != null && !clazz.equals(Model.class)) {

            String dbName = MetaModel.getDbName(clazz);
            if (modelClasses.get(dbName) == null) {
                modelClasses.put(dbName, new ArrayList<Class<? extends Model>>());
            }
            if(!modelClasses.get(dbName).contains(clazz)){
                modelClasses.get(dbName).add(clazz);
            }
        }
    }
    
    public static void registerModelClasses(String dbName) throws IOException, ClassNotFoundException {
    	
    	if (modelClasses.get(dbName) == null) {
            modelClasses.put(dbName, new HashSet<Class<? extends Model>>());
        }
    	
    	Collection<String> classNames = Registry.instance().getConfiguration().getModelNames(dbName);
    	if(classNames == null || classNames.size() == 0) {
    		LogFilter.log(logger, "ActiveJDBC Warning: Cannot find any model for db," + dbName);
    	}
    	Collection<Class<? extends Model>> dbModels = modelClasses.get(dbName);
    	for(String className : classNames) {
	    	Class clazz = Class.forName(className);
	        if (Model.class.isAssignableFrom(clazz) && clazz != null && !clazz.equals(Model.class)) {
	            	dbModels.add(clazz);
	        }
        }
    }
    
    static void unregisterModelClasses(String dbName) {
    	modelClasses.remove(dbName);
    }
    
    

}
