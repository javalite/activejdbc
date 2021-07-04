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

import org.javalite.activejdbc.associations.*;
import org.javalite.activejdbc.cache.QueryCache;
import org.javalite.activejdbc.logging.LogFilter;
import org.javalite.common.Inflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import static org.javalite.activejdbc.ModelDelegate.metaModelOf;
import static org.javalite.common.Util.*;


/**
 * While this class is public, it is never instantiated directly. This class provides
 * a number of APIs for augmenting the query.
 *
 * @author Igor Polevoy
 * @author Eric Nielsen
 */
public class LazyList<T extends Model> extends AbstractLazyList<T> implements Externalizable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LazyList.class);
    private final List<String> orderBys = new ArrayList<>();
    private final MetaModel metaModel;
    private final String subQuery;
    private final String fullQuery;
    private final Object[] params;
    private long limit = -1, offset = -1;
    private final List<Association> includes = new ArrayList<>();
    private final boolean forPaginator;
    private final Map<String, List<String>> associationOrderBys = new HashMap<>();;

    protected LazyList(String subQuery, MetaModel metaModel, Object... params) {
        this.fullQuery = null;
        this.subQuery = subQuery;
        this.params = params == null? new Object[]{}: params;
        this.metaModel = metaModel;
        this.forPaginator = false;
    }
    protected LazyList(String subQuery, MetaModel metaModel, List<String> orderBys, Object... params) {
        this.fullQuery = null;
        this.subQuery = subQuery;
        this.params = params == null? new Object[]{}: params;
        this.metaModel = metaModel;
        this.forPaginator = false;
        this.orderBys.addAll(orderBys);
    }

    /**
     *
     * @param metaModel
     * @param fullQuery
     * @param forPaginator true is this list should not check usage of limit() and offset() methods.
     * @param params
     */
    protected LazyList(boolean forPaginator, MetaModel metaModel, String fullQuery, Object... params) {
        this.fullQuery = fullQuery;
        this.subQuery = null;
        this.params = params == null? new Object[]{}: params;
        this.metaModel = metaModel;
        this.forPaginator = forPaginator;
    }

    /**
     * This is only used by SuperLazyList
     */
    protected LazyList() {
        delegate = new ArrayList<>();
        this.fullQuery = null;
        this.subQuery = null;
        this.params = null;
        this.metaModel = null;
        this.forPaginator = false;
    }

    /**
     *  This method limits the number of results in the resultset.
     *  It can be used in combination with the offset like this:
     *
     *  <code>List&lt;Event&gt; events =  Event.find("mnemonic = ?", "GLUC").offset(101).limit(20).orderBy("history_event_id");</code>
     *  This will produce 20 records, starting from record 101. This is an efficient method, it will only retrieve records
     *  that are necessary.
     *
     * @param limit how many records to retrieve.
     * @return instance of this <code>LazyList</code>
     */
    public <E extends Model>  LazyList<E> limit(long limit){
        if(fullQuery != null && !forPaginator) throw new IllegalArgumentException("Cannot use .limit() if using free form SQL");

        if(limit < 0) throw new IllegalArgumentException("limit cannot be negative");

        this.limit = limit;
        return (LazyList<E>) this;
    }

    /**
     * This method sets an offset of a resultset. For instance, if the offset is 101, then the resultset will skip the
     * first 100 records.
     * It can be used in combination wit the limit like this:
     *
     *  <code>List<Event>  events =  Event.find("mnemonic = ?", "GLUC").offset(101).limit(20).orderBy("history_event_id");</code>
     *  This will produce 20 records, starting from record 101. This is an efficient method, it will only retrieve records
     *  that are necessary.
     *
     * @param offset
     * @return instance of this <code>LazyList</code>
     */
    public <E extends Model>  LazyList<E> offset(long offset){
        if(fullQuery != null && !forPaginator) throw new IllegalArgumentException("Cannot use .offset() if using free form SQL");

        if(offset < 0) throw new IllegalArgumentException("offset cannot be negative");

        this.offset = offset;
        return (LazyList<E>) this;
    }

    /**
     * Use this method to order results by a column. These methods can be chained:
     * <code>Person.find(...).orderBy("department").orderBy("age")</code>
     *
     * @param orderBy order by clause. Examples: "department", "age desc", etc.
     * @return instance of this <code>LazyList</code>
     */
    public <E extends Model>  LazyList<E> orderBy(String orderBy){
        if(fullQuery != null && !forPaginator) throw new IllegalArgumentException("Cannot use .orderBy() if using free form SQL");

        orderBys.add(orderBy);
        return (LazyList<E>) this;
    }


    /**
     * Use this method to order associated object results by a column. These methods can be chained:
     * <code>Person.find(...).orderAssociationBy(Author.class, "first_name").orderAssociationBy(Comment.class, "date")</code>
     *
     * @param associate a dependent class. This class represents a model with which a current model has a relationship.
     * @param orderBy order by clause. Examples: "department", "age desc", etc.
     * @return instance of this <code>LazyList</code>
     */
    public <E extends Model>  LazyList<E> orderAssociationBy(Class<? extends Model> associate, String orderBy){
        for (Class<? extends Model> clazz : classes) {
            if(!metaModel.isAssociatedTo(clazz)) throw new IllegalArgumentException("Model: " + clazz.getName() + " is not associated with: " + metaModel.getModelClass().getName());

        }

        String associateName = associate.getName();
        List<String> orders = associationOrderBys.computeIfAbsent(associateName, a -> new ArrayList<>());
        orders.add(orderBy);
        associationOrderBys.put(associateName, orders);
    }


    /**
     * This method includes associated objects. It will eagerly load associated models of
     * models selected by the query. For instance, if there are models <code>Author</code>, <code>Post</code>
     * and <code>Comment</code>, where <code>Author</code> has many <code>Post</code>s and <code> Post</code>
     * has many <code>Comment</code>s, then this query:
     * <pre>
     * List<Post> todayPosts = Post.where("post_date = ?", today).include(Author.class, Comment.class);
     * </pre>
     *
     * or:
     *
     * <pre>
     * List<Post> todayPosts = Post.where("post_date = ?", today).include(Author.class).include(Comment.class);
     * </pre>
     *
     * will generate only three queries to database - one per model. All the dependencies (includes) will be
     * eagerly loaded, and iteration via the <code>todayPosts</code> list will not generate any more queries,
     * even when a post author and comments are requested. Use this with caution as this method can allocate
     * a lot of memory (obviously).
     *
     * <p></p>
     *
     * This method will not follow relationships of related models, but rather only relationships of the current
     * one.
     *
     * @param classes list of dependent classes. These classes represent models with which a current model has a
     * relationship.
     * @return instance of this <code>LazyList</code>
     */
    public <E extends Model> LazyList<E> include(Class<? extends Model>... classes) {

        for (Class<? extends Model> clazz : classes) {
            if(!metaModel.isAssociatedTo(clazz)) throw new IllegalArgumentException("Model: " + clazz.getName() + " is not associated with: " + metaModel.getModelClass().getName());

        }

        //lets cache included classes and associations for future processing.
        for (Class includeClass : classes) {
            includes.addAll(metaModel.getAssociationsForTarget(includeClass));
        }

        return (LazyList<E>)this;
    }

    /**
     * Converts the resultset to list of maps, where each map represents a row in the resultset keyed off column names.
     *
     * @return list of maps, where each map represents a row in the resultset keyed off column names.
     */
    public List<Map<String, Object>> toMaps() {
        hydrate();
        List<Map<String, Object>> maps = new ArrayList<>(delegate.size());
        for (T t : delegate) {
            maps.add(t.toMap());
        }
        return maps;
    }

    /**
     * Generates a XML document from content of this list.
     *
     * @param pretty pretty format (human readable), or one line text.
     * @param declaration true to include XML declaration at the top
     * @param attrs list of attributes to include. No arguments == include all attributes.
     * @return generated XML.
     */
    public String toXml(boolean pretty, boolean declaration, String... attrs) {
        String topNode = Inflector.pluralize(Inflector.underscore(metaModel.getModelClass().getSimpleName()));

        hydrate();

        StringBuilder sb = new StringBuilder();
        if(declaration) {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            if (pretty) sb.append('\n');
        }
        sb.append('<').append(topNode).append('>');
        if (pretty) { sb.append('\n'); }
        for (T t : delegate) {
            t.toXmlP(sb, pretty, pretty ? "  " : "", attrs);
        }
        sb.append("</").append(topNode).append('>');
        if (pretty) { sb.append('\n'); }
        return sb.toString();
    }


    /**
     * Generates JSON from content of this list
     *
     * @param pretty true if you want pretty format, false if not
     * @param attrs attributes to include, not providing any will include all.
     * @return generated JSON
     */
    public String toJson(boolean pretty, String ... attrs) {
        hydrate();
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        if (pretty) sb.append('\n');

        for (int i = 0; i < delegate.size(); i++) {
            if (i > 0) {
                sb.append(',');
                if (pretty) { sb.append('\n'); }
            }
            delegate.get(i).toJsonP(sb, pretty, (pretty ? "  " : ""), attrs);
        }
        if (pretty) { sb.append('\n'); }
        sb.append(']');
        return sb.toString();
    }


    /**
     * This method exists to force immediate load from DB. Example;
     * <code> Person.find("name = ?", "Smith").load();</code>.
     * It is not possible to call other methods after load(). The load() method should be the last to be called in the chain:
     * <code> Person.find("name = ?", "Smith").limit(10).load();</code>.
     * This: will generate exception: <code> Person.find("name = ?", "Smith").load().limit();</code>.
     *
     * @return fully loaded list.
     */
    public <E extends Model> LazyList<E> load() {
        if (hydrated()) { throw new DBException("load() must be the last on the chain of methods"); }
        hydrate();
        return (LazyList<E>) this;
    }

    /**
     * Same as <code>toSql(true)</code>, see {@link #toSql(boolean)};
     *
     * @return SQL in a dialect for current connection which will be used if you start querying this
     * list.
     */
    public String toSql() {
        return toSql(true);
    }

    /**
     * Use to see what SQL will be sent to the database.
     *
     * @param showParameters true to see parameter values, false not to.
     * @return SQL in a dialect for current connection which will be used if you start querying this
     * list.
     */
    public String toSql(boolean showParameters) {
        String sql;
        if(forPaginator){
            sql = metaModel.getDialect().formSelect(null, null, fullQuery, orderBys, limit, offset);
        }else{
            sql = fullQuery != null ? fullQuery
                    : metaModel.getDialect().formSelect(metaModel.getTableName(), null, subQuery, orderBys, limit, offset);
        }
        if (showParameters) {
            StringBuilder sb = new StringBuilder(sql).append(", with parameters: ");
            join(sb, params, ", ");
            sql = sb.toString();
        }
        return sql;
    }


    @Override
    protected void hydrate() {

        if (hydrated()) { return; }

        String sql= toSql(false);

        if(metaModel.cached()){
            List<T> cached = (List<T>) QueryCache.instance().getItem(metaModel.getTableName(), sql, params);
            if(cached != null){
                delegate = cached;
                LogFilter.logQuery(LOGGER, sql, params, System.currentTimeMillis(), true);
                return;
            }
        }
        delegate = new ArrayList<>();
        long start = System.currentTimeMillis();
        new DB(metaModel.getDbName()).find(sql, params).with(new RowListenerAdapter() {
            @Override public void onNext(Map<String, Object> map) {
                delegate.add(ModelDelegate.<T>instance(map, metaModel));
            }
        });
        LogFilter.logQuery(LOGGER, sql, params, start, false);
        if(metaModel.cached()){
            delegate = Collections.unmodifiableList(delegate);
            QueryCache.instance().addItem(metaModel.getTableName(), sql, params, delegate);
        }
        processIncludes();
    }

    private boolean hydrated() {
        return delegate != null;
    }

    private void processIncludes() {
        for (Association association : includes) {
            if (association instanceof BelongsToAssociation) {
                processParent((BelongsToAssociation) association);
            } else if (association instanceof OneToManyAssociation) {
                processChildren((OneToManyAssociation) association);
            } else if (association instanceof Many2ManyAssociation) {
                processManyToMany((Many2ManyAssociation) association);
            } else if (association instanceof OneToManyPolymorphicAssociation) {
                processPolymorphicChildren((OneToManyPolymorphicAssociation) association);
            } else if (association instanceof BelongsToPolymorphicAssociation) {
                processPolymorphicParent((BelongsToPolymorphicAssociation) association);
            }
        }
    }

    /**
     * @author Evan Leonard
     */
    private void processPolymorphicParent(BelongsToPolymorphicAssociation association) {
        if (delegate.isEmpty()) { // no need to process children if no models selected.
            return;
        }
        //need to remove duplicates because more than one child can belong to the same parent.
        Set<Object> distinctParentIds = collectDistinct("parent_id", "parent_type", association.getParentClassName());
        distinctParentIds.remove(null); // remove null parent id
        if (distinctParentIds.isEmpty()) {
            return;
        }
        final MetaModel parentMetaModel = metaModelOf(association.getTargetClass());
        final Map<Object, Model> parentById = new HashMap<>();

        StringBuilder query = new StringBuilder().append(parentMetaModel.getIdName()).append(" IN (");
        appendQuestions(query, distinctParentIds.size());
        query.append(')');
        for (Model parent : new LazyList<>(query.toString(), parentMetaModel, associationOrderBys.computeIfAbsent(association.getTargetClass().getName(), a -> new ArrayList<>()), distinctParentIds.toArray())) {
            parentById.put(association.getParentClassName() + ":" + parent.getId(), parent);
        }

        //now that we have the parents in the has, we need to distribute them into list of children that are
        //stored in the delegate.
        for (Model child : delegate) {
            // parent could be null, which is fine
            child.setCachedParent(parentById.get(association.getParentClassName() + ":" + child.get("parent_id")));
        }
    }

    private void processParent(BelongsToAssociation association) {
        if (delegate.isEmpty()) { // no need to process parents if no models selected.
            return;
        }
        //need to remove duplicates because more than one child can belong to the same parent.
        Set<Object> distinctParentIds = collectDistinct(association.getFkName());
        distinctParentIds.remove(null); // remove null parent id
        if (distinctParentIds.isEmpty()) {
            return;
        }
        final MetaModel parentMetaModel = metaModelOf(association.getTargetClass());
        final Map<Object, Model> parentById = new HashMap<>();

        StringBuilder query = new StringBuilder().append(parentMetaModel.getIdName()).append(" IN (");
        appendQuestions(query, distinctParentIds.size());
        query.append(')');
        for (Model parent : new LazyList<>(query.toString(), parentMetaModel, associationOrderBys.computeIfAbsent(association.getTargetClass().getName(), a -> new ArrayList<>()), distinctParentIds.toArray())) {
            parentById.put(parent.getId(), parent);
        }
        //now that we have the parents in the has, we need to distribute them into list of children that are
        //stored in the delegate.
        for (Model child : delegate) {
            // parent could be null, which is fine
            child.setCachedParent(parentById.get(child.get(association.getFkName())));
        }
    }

    /**
     * Collects values from a result set that correspond to a attribute name.
     * For example, if a list contains collection of <code>Person</code> models, then
     * you can collect first names like this:
     * <pre>
     * List firstNames = Person.findAll().collect("first_name");
     * </pre>
     * provided that the corresponding table has a column <code>first_name</code>.
     * <p><p/>
     * Keep in mind, that if all you need is a one column data, this method of getting it is not
     * the most efficient (because since you are using a model, you will query all columns from a table,
     * but will use only one). In these cases, you might want to consider {@link Base#firstColumn(String, Object...)} and
     * {@link DB#firstColumn(String, Object...)}.
     *
     * @param attributeName name of attribute to collect.
     * @return list of collected values for a column.
     */
    public List collect(String attributeName) {
        List results = new ArrayList();
        collect(results, attributeName);
        return results;
    }

    public Set collectDistinct(String attributeName) {
        Set results = new LinkedHashSet();
        collect(results, attributeName);
        return results;
    }

    private void collect(Collection results, String attributeName) {
        hydrate();
        for (Model model : delegate) {
            results.add(model.get(attributeName));
        }
    }

    public List collect(String attributeName, String filterAttribute, Object filterValue) {
        List results = new ArrayList();
        collect(results, attributeName, filterAttribute, filterValue);
        return results;
    }

    public Set collectDistinct(String attributeName, String filterAttribute, Object filterValue) {
        Set results = new LinkedHashSet();
        collect(results, attributeName, filterAttribute, filterValue);
        return results;
    }

    private void collect(Collection results, String attributeName, String filterAttribute, Object filterValue) {
        hydrate();
        for (Model model : delegate) {
            if (model.get(filterAttribute).equals(filterValue)) {
                results.add(model.get(attributeName));
            }
        }
    }

    private void appendQuestions(StringBuilder sb, int count) {
        joinAndRepeat(sb, "?", ", ", count);
    }

    private void processPolymorphicChildren(OneToManyPolymorphicAssociation association) {
        if (delegate.isEmpty()) {//no need to process children if no models selected.
            return;
        }
        MetaModel childMetaModel = metaModelOf(association.getTargetClass());
        Map<Object, List<Model>> childrenByParentId = new HashMap<>();
        List<Object> ids = collect(metaModel.getIdName());
        StringBuilder query = new StringBuilder().append("parent_id IN (");
        appendQuestions(query, ids.size());
        query.append(") AND parent_type = '").append(association.getTypeLabel()).append('\'');

        List<String> orders = associationOrderBys.computeIfAbsent(association.getTargetClass().getName(), a -> new ArrayList<>());
        orders.add(0, childMetaModel.getIdName());
        for (Model child : new LazyList<>(query.toString(), childMetaModel, orders, ids.toArray())) {
            if (childrenByParentId.get(child.get("parent_id")) == null) {
                childrenByParentId.put(child.get("parent_id"), new SuperLazyList<>());
            }
            childrenByParentId.get(child.get("parent_id")).add(child);
        }

        for (T parent : delegate) {
            List<Model> children = childrenByParentId.get(parent.getId());
            if (children != null) {
                parent.setChildren(childMetaModel.getModelClass(), children);
            }
            else {
                parent.setChildren(childMetaModel.getModelClass(), new SuperLazyList<>());
            }
        }
    }


    private void processChildren(OneToManyAssociation association) {
        if(delegate.isEmpty()){//no need to process children if no models selected.
            return;
        }
        final Class<? extends Model> associate = association.getTargetClass();
        final MetaModel childMetaModel = metaModelOf(associate);
        final String fkName = association.getFkName();
        final Map<Object, List<Model>> childrenByParentId = new HashMap<>();
        List<Object> ids = collect(metaModel.getIdName());
        StringBuilder query = new StringBuilder().append(fkName).append(" IN (");
        appendQuestions(query, ids.size());
        query.append(')');
        List<String> orders = associationOrderBys.computeIfAbsent(associate.getName(), a -> new ArrayList<>());
        orders.add(0, childMetaModel.getIdName());
        for (Model child : new LazyList<>(query.toString(), childMetaModel, orders, ids.toArray())) {
            if(childrenByParentId.get(child.get(fkName)) == null){
                childrenByParentId.put(child.get(fkName), new SuperLazyList<>());
            }
            childrenByParentId.get(child.get(fkName)).add(child);
        }
        for(T parent : delegate){
            List<Model> children = childrenByParentId.get(parent.getId());
            if(children != null){
                parent.setChildren(childMetaModel.getModelClass(), children);
            }
            else {
                parent.setChildren(childMetaModel.getModelClass(), new SuperLazyList<>());
            }
        }
    }

    private void processManyToMany(Many2ManyAssociation association) {
        if(delegate.isEmpty()){//no need to process other if no models selected.
            return;
        }
        final Class<? extends Model> associate = association.getTargetClass();
        final MetaModel childMetaModel = metaModelOf(associate);
        final Map<Object, List<Model>> childrenByParentId = new HashMap<>();
        List<Object> ids = collect(metaModel.getIdName());
        List<String> orders = associationOrderBys.computeIfAbsent(associate.getName(), a -> new ArrayList<>());
        orders.add(0, childMetaModel.getIdName());
        List<Map> childResults = new DB(childMetaModel.getDbName()).findAll(childMetaModel.getDialect().selectManyToManyAssociation(
                association, "the_parent_record_id", ids.size(), orders), ids.toArray());
        for(Map res: childResults){
            Model child = ModelDelegate.instance(res, childMetaModel);
            Object parentId = res.get("the_parent_record_id");
            if(childrenByParentId.get(parentId) == null){
                childrenByParentId.put(parentId, new SuperLazyList<>());
            }
            childrenByParentId.get(parentId).add(child);
        }
        for(T parent : delegate){
            List<Model> children = childrenByParentId.get(parent.getId());
            if (children != null) {
                parent.setChildren(childMetaModel.getModelClass(), children);
            }
            else {
                parent.setChildren(childMetaModel.getModelClass(), new SuperLazyList<>());
            }
        }
    }

    /**
     * Dumps contents of this list to <code>System.out</code>.
     */
    public void dump(){
        dump(System.out);
    }

    /**
     * Dumps content of list to a stream. Use for debugging.
     * @param out
     */
    public void dump(OutputStream out){
        hydrate();
        PrintWriter p = new PrintWriter(out);
        for(Model m : delegate){
            p.write(m.toString());
            p.write('\n');
        }
        p.flush();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(delegate);

    }

    @Override @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        delegate = (List<T>) in.readObject();
    }
}
