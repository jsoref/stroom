/*
 * This file is generated by jOOQ.
 */
package stroom.security.impl.db.jooq.tables.records;


import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.Row4;
import org.jooq.impl.UpdatableRecordImpl;

import stroom.security.impl.db.jooq.tables.StroomUser;


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
public class StroomUserRecord extends UpdatableRecordImpl<StroomUserRecord> implements Record4<Long, String, String, Boolean> {

    private static final long serialVersionUID = 187173420;

    /**
     * Setter for <code>stroom.stroom_user.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.stroom_user.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>stroom.stroom_user.name</code>.
     */
    public void setName(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.stroom_user.name</code>.
     */
    public String getName() {
        return (String) get(1);
    }

    /**
     * Setter for <code>stroom.stroom_user.uuid</code>.
     */
    public void setUuid(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>stroom.stroom_user.uuid</code>.
     */
    public String getUuid() {
        return (String) get(2);
    }

    /**
     * Setter for <code>stroom.stroom_user.is_group</code>.
     */
    public void setIsGroup(Boolean value) {
        set(3, value);
    }

    /**
     * Getter for <code>stroom.stroom_user.is_group</code>.
     */
    public Boolean getIsGroup() {
        return (Boolean) get(3);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Row4<Long, String, String, Boolean> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row4<Long, String, String, Boolean> valuesRow() {
        return (Row4) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field1() {
        return StroomUser.STROOM_USER.ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field2() {
        return StroomUser.STROOM_USER.NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field3() {
        return StroomUser.STROOM_USER.UUID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Boolean> field4() {
        return StroomUser.STROOM_USER.IS_GROUP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component1() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component2() {
        return getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component3() {
        return getUuid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean component4() {
        return getIsGroup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value1() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value2() {
        return getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value3() {
        return getUuid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean value4() {
        return getIsGroup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StroomUserRecord value1(Long value) {
        setId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StroomUserRecord value2(String value) {
        setName(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StroomUserRecord value3(String value) {
        setUuid(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StroomUserRecord value4(Boolean value) {
        setIsGroup(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StroomUserRecord values(Long value1, String value2, String value3, Boolean value4) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached StroomUserRecord
     */
    public StroomUserRecord() {
        super(StroomUser.STROOM_USER);
    }

    /**
     * Create a detached, initialised StroomUserRecord
     */
    public StroomUserRecord(Long id, String name, String uuid, Boolean isGroup) {
        super(StroomUser.STROOM_USER);

        set(0, id);
        set(1, name);
        set(2, uuid);
        set(3, isGroup);
    }
}
