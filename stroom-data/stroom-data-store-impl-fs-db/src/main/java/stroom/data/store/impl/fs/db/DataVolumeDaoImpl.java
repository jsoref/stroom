package stroom.data.store.impl.fs.db;

import org.jooq.Condition;
import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FindDataVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.db.util.JooqUtil;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaVolume.FS_META_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;

public class DataVolumeDaoImpl implements DataVolumeDao {
    private final ConnectionProvider connectionProvider;

    @Inject
    DataVolumeDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public BaseResultList<DataVolume> find(final FindDataVolumeCriteria criteria) {
        final List<Condition> conditions = new ArrayList<>();
        JooqUtil.getSetCondition(FS_META_VOLUME.FS_VOLUME_ID, criteria.getVolumeIdSet()).ifPresent(conditions::add);
        JooqUtil.getSetCondition(FS_META_VOLUME.META_ID, criteria.getMetaIdSet()).ifPresent(conditions::add);

        return JooqUtil.contextResult(connectionProvider, context -> {
            final List<DataVolume> list = context.select(FS_META_VOLUME.META_ID, FS_VOLUME.PATH)
                    .from(FS_META_VOLUME)
                    .join(FS_VOLUME).on(FS_VOLUME.ID.eq(FS_META_VOLUME.FS_VOLUME_ID))
                    .where(conditions)
                    .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                    .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                    .fetch()
                    .map(r -> new DataVolumeImpl(r.get(FS_META_VOLUME.META_ID), r.get(FS_VOLUME.PATH)));
            return BaseResultList.createCriterialBasedList(list, criteria);
        });
    }

    /**
     * Return the meta data volumes for a meta id.
     */
    @Override
    public DataVolume findDataVolume(final long metaId) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(FS_META_VOLUME.META_ID, FS_VOLUME.PATH)
                .from(FS_META_VOLUME)
                .join(FS_VOLUME).on(FS_VOLUME.ID.eq(FS_META_VOLUME.FS_VOLUME_ID))
                .where(FS_META_VOLUME.META_ID.eq(metaId))
                .fetchOptional()
                .map(r -> new DataVolumeImpl(r.get(FS_META_VOLUME.META_ID), r.get(FS_VOLUME.PATH)))
                .orElse(null));
    }

    @Override
    public DataVolume createDataVolume(final long dataId, final FsVolume volume) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            context.insertInto(FS_META_VOLUME, FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                        .values(dataId, volume.getId())
                        .execute();
                return new DataVolumeImpl(dataId, volume.getPath());
        });
    }

    class DataVolumeImpl implements DataVolume {
        private final long streamId;
        private final String volumePath;

        DataVolumeImpl(final long streamId,
                       final String volumePath) {
            this.streamId = streamId;
            this.volumePath = volumePath;
        }

        @Override
        public long getStreamId() {
            return streamId;
        }

        @Override
        public String getVolumePath() {
            return volumePath;
        }
    }
}
