/*
 * This file is generated by jOOQ.
 */
package stroom.explorer.impl.db.jooq;


import javax.annotation.Generated;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;

import stroom.explorer.impl.db.jooq.tables.ExplorerNode;
import stroom.explorer.impl.db.jooq.tables.ExplorerPath;


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

    public static final Index EXPLORER_NODE_PRIMARY = Indexes0.EXPLORER_NODE_PRIMARY;
    public static final Index EXPLORER_NODE_TYPE = Indexes0.EXPLORER_NODE_TYPE;
    public static final Index EXPLORER_PATH_PRIMARY = Indexes0.EXPLORER_PATH_PRIMARY;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index EXPLORER_NODE_PRIMARY = Internal.createIndex("PRIMARY", ExplorerNode.EXPLORER_NODE, new OrderField[] { ExplorerNode.EXPLORER_NODE.ID }, true);
        public static Index EXPLORER_NODE_TYPE = Internal.createIndex("type", ExplorerNode.EXPLORER_NODE, new OrderField[] { ExplorerNode.EXPLORER_NODE.TYPE, ExplorerNode.EXPLORER_NODE.UUID }, true);
        public static Index EXPLORER_PATH_PRIMARY = Internal.createIndex("PRIMARY", ExplorerPath.EXPLORER_PATH, new OrderField[] { ExplorerPath.EXPLORER_PATH.ANCESTOR, ExplorerPath.EXPLORER_PATH.DESCENDANT }, true);
    }
}
