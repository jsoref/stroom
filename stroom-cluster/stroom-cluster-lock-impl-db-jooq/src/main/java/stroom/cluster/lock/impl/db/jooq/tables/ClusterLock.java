/*
 * This file is generated by jOOQ.
 */
package stroom.cluster.lock.impl.db.jooq.tables;


import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import stroom.cluster.lock.impl.db.jooq.Indexes;
import stroom.cluster.lock.impl.db.jooq.Keys;
import stroom.cluster.lock.impl.db.jooq.Stroom;
import stroom.cluster.lock.impl.db.jooq.tables.records.ClusterLockRecord;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.9"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ClusterLock extends TableImpl<ClusterLockRecord> {

    private static final long serialVersionUID = -346151106;

    /**
     * The reference instance of <code>stroom.cluster_lock</code>
     */
    public static final ClusterLock CLUSTER_LOCK = new ClusterLock();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<ClusterLockRecord> getRecordType() {
        return ClusterLockRecord.class;
    }

    /**
     * The column <code>stroom.cluster_lock.id</code>.
     */
    public final TableField<ClusterLockRecord, Integer> ID = createField("id", org.jooq.impl.SQLDataType.INTEGER.nullable(false).identity(true), this, "");

    /**
     * The column <code>stroom.cluster_lock.version</code>.
     */
    public final TableField<ClusterLockRecord, Integer> VERSION = createField("version", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>stroom.cluster_lock.name</code>.
     */
    public final TableField<ClusterLockRecord, String> NAME = createField("name", org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * Create a <code>stroom.cluster_lock</code> table reference
     */
    public ClusterLock() {
        this(DSL.name("cluster_lock"), null);
    }

    /**
     * Create an aliased <code>stroom.cluster_lock</code> table reference
     */
    public ClusterLock(String alias) {
        this(DSL.name(alias), CLUSTER_LOCK);
    }

    /**
     * Create an aliased <code>stroom.cluster_lock</code> table reference
     */
    public ClusterLock(Name alias) {
        this(alias, CLUSTER_LOCK);
    }

    private ClusterLock(Name alias, Table<ClusterLockRecord> aliased) {
        this(alias, aliased, null);
    }

    private ClusterLock(Name alias, Table<ClusterLockRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> ClusterLock(Table<O> child, ForeignKey<O, ClusterLockRecord> key) {
        super(child, key, CLUSTER_LOCK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return Stroom.STROOM;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.CLUSTER_LOCK_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<ClusterLockRecord, Integer> getIdentity() {
        return Keys.IDENTITY_CLUSTER_LOCK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<ClusterLockRecord> getPrimaryKey() {
        return Keys.KEY_CLUSTER_LOCK_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<ClusterLockRecord>> getKeys() {
        return Arrays.<UniqueKey<ClusterLockRecord>>asList(Keys.KEY_CLUSTER_LOCK_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TableField<ClusterLockRecord, Integer> getRecordVersion() {
        return VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClusterLock as(String alias) {
        return new ClusterLock(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClusterLock as(Name alias) {
        return new ClusterLock(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public ClusterLock rename(String name) {
        return new ClusterLock(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public ClusterLock rename(Name name) {
        return new ClusterLock(name, null);
    }
}
