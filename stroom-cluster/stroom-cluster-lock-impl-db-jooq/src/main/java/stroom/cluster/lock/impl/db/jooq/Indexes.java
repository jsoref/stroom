/*
 * This file is generated by jOOQ.
 */
package stroom.cluster.lock.impl.db.jooq;


import javax.annotation.Generated;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;

import stroom.cluster.lock.impl.db.jooq.tables.ClusterLock;


/**
 * A class modelling indexes of tables of the <code>stroom</code> schema.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.9"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index CLUSTER_LOCK_PRIMARY = Indexes0.CLUSTER_LOCK_PRIMARY;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index CLUSTER_LOCK_PRIMARY = Internal.createIndex("PRIMARY", ClusterLock.CLUSTER_LOCK, new OrderField[] { ClusterLock.CLUSTER_LOCK.ID }, true);
    }
}
