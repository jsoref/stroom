/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.volume;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.node.NodeCache;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.node.shared.VolumeState;
import stroom.persist.EntityManagerSupport;
import stroom.security.Security;
import stroom.security.SecurityImpl;
import stroom.security.impl.mock.MockSecurityContext;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestVolumeServiceImpl extends StroomUnitTest {
    private static final Path DEFAULT_VOLUMES_PATH;
    private static final Path DEFAULT_INDEX_VOLUME_PATH;
    private static final Path DEFAULT_STREAM_VOLUME_PATH;

    static {
        DEFAULT_VOLUMES_PATH = FileUtil.getTempDir().resolve(VolumeServiceImpl.DEFAULT_VOLUMES_SUBDIR);
        DEFAULT_INDEX_VOLUME_PATH = DEFAULT_VOLUMES_PATH.resolve(VolumeServiceImpl.DEFAULT_INDEX_VOLUME_SUBDIR);
        DEFAULT_STREAM_VOLUME_PATH = DEFAULT_VOLUMES_PATH.resolve(VolumeServiceImpl.DEFAULT_STREAM_VOLUME_SUBDIR);
    }

    private final Rack rack1 = Rack.create("rack1");
    private final Rack rack2 = Rack.create("rack2");
    private final Node node1a = Node.create(rack1, "1a");
    private final Node node1b = Node.create(rack1, "1b");
    private final Node node1c = Node.create(rack1, "1c");
    private final Node node2a = Node.create(rack2, "2a");
    private final Node node2b = Node.create(rack2, "2b");
    private final VolumeEntity public1a = VolumeEntity.create(node1a, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_1A")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final VolumeEntity public1b = VolumeEntity.create(node1b, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_1B")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final VolumeEntity public2a = VolumeEntity.create(node2a, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_2A")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final VolumeEntity public2b = VolumeEntity.create(node2b, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_2B")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final Security security = new SecurityImpl(new MockSecurityContext());
    private VolumeConfig volumeConfig = new VolumeConfig();
    private MockVolumeService volumeServiceImpl = null;
    @Mock
    private StroomEntityManager stroomEntityManager;
    @Mock
    private EntityManagerSupport entityManagerSupport;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        deleteDefaultVolumesDir();

        final List<VolumeEntity> volumeList = new ArrayList<>();
        volumeList.add(public1a);
        volumeList.add(public1b);
        volumeList.add(public2a);
        volumeList.add(public2b);

        volumeConfig.setResilientReplicationCount(2);

        volumeServiceImpl = new MockVolumeService(stroomEntityManager, security, entityManagerSupport, new NodeCache(node1a), volumeConfig, null);
        volumeServiceImpl.volumeList = volumeList;
    }

    @Test
    void testNode1aNodeWithCacheAndSomePuckerLocalStorage() {
        final Set<VolumeEntity> call1 = volumeServiceImpl.getStreamVolumeSet(node1a);
        final Set<VolumeEntity> call2 = volumeServiceImpl.getStreamVolumeSet(node1a);
        assertThat(call1.size()).isEqualTo(2);
        assertThat(call2.size()).isEqualTo(2);

        // Check that we only write once in a rack
        assertThat(call1.contains(public1a) ^ call1.contains(public1b)).isTrue();
        assertThat(call1.contains(public2a) ^ call1.contains(public2b)).isTrue();
        assertThat(call2.contains(public1a) ^ call2.contains(public1b)).isTrue();
        assertThat(call2.contains(public2a) ^ call2.contains(public2b)).isTrue();

        // Check that we round robin OK
        assertThat(call1.contains(public2a) ^ call2.contains(public2a)).isTrue();
        assertThat(call1.contains(public2b) ^ call2.contains(public2b)).isTrue();
    }

    @Test
    void testNode1cNodeWithNoStorage() {
        final Set<VolumeEntity> call1 = volumeServiceImpl.getStreamVolumeSet(node1c);
        final Set<VolumeEntity> call2 = volumeServiceImpl.getStreamVolumeSet(node1c);
        assertThat(call1.size()).isEqualTo(2);
        assertThat(call2.size()).isEqualTo(2);

        // Check that we only write once in a rack
        assertThat(call1.contains(public1a) ^ call1.contains(public1b)).isTrue();
        assertThat(call1.contains(public2a) ^ call1.contains(public2b)).isTrue();
        assertThat(call2.contains(public1a) ^ call2.contains(public1b)).isTrue();
        assertThat(call2.contains(public2a) ^ call2.contains(public2b)).isTrue();

        // Check that we round robin OK
        assertThat(call1.contains(public1a) ^ call2.contains(public1a)).isTrue();
        assertThat(call1.contains(public1b) ^ call2.contains(public1b)).isTrue();
        assertThat(call1.contains(public2a) ^ call2.contains(public2a)).isTrue();
        assertThat(call1.contains(public2b) ^ call2.contains(public2b)).isTrue();
    }

    @Test
    void testNode2aNodeWithNoCache() {
        final Set<VolumeEntity> call1 = volumeServiceImpl.getStreamVolumeSet(node2a);
        final Set<VolumeEntity> call2 = volumeServiceImpl.getStreamVolumeSet(node2a);
        assertThat(call1.size()).isEqualTo(2);
        assertThat(call2.size()).isEqualTo(2);

        // Check that we only write once in a rack
        assertThat(call1.contains(public1a) ^ call1.contains(public1b)).isTrue();
        assertThat(call1.contains(public2a) ^ call1.contains(public2b)).isTrue();
        assertThat(call2.contains(public1a) ^ call2.contains(public1b)).isTrue();
        assertThat(call2.contains(public2a) ^ call2.contains(public2b)).isTrue();

        // Check that we round robin OK on rack 1
        assertThat(call1.contains(public1a) ^ call2.contains(public1a)).isTrue();
        assertThat(call1.contains(public1b) ^ call2.contains(public1b)).isTrue();
    }

    @Test
    void testStartup_Disabled() {
        volumeConfig.setCreateDefaultOnStart(false);

        assertThat(volumeServiceImpl.saveCalled).isFalse();
        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isFalse();
        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isFalse();
    }

    @Test
    void testStartup_EnabledExistingVolumes() {
        volumeConfig.setCreateDefaultOnStart(true);

        assertThat(volumeServiceImpl.saveCalled).isFalse();
        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isFalse();
        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isFalse();
    }

    @Test
    void testStartup_EnabledNoExistingVolumes() {
        volumeConfig.setCreateDefaultOnStart(true);
        volumeServiceImpl.volumeList.clear();
        volumeServiceImpl.getStreamVolumeSet(node1a);
//        volumeServiceImpl.startup();

        assertThat(volumeServiceImpl.saveCalled).isTrue();
        //make sure both paths have been saved
        assertThat(volumeServiceImpl.savedVolumes.stream()
                .map(VolumeEntity::getPath)
                .filter(path -> path.equals(FileUtil.getCanonicalPath(DEFAULT_INDEX_VOLUME_PATH)) ||
                        path.equals(FileUtil.getCanonicalPath(DEFAULT_STREAM_VOLUME_PATH)))
                .count()).isEqualTo(2);
        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isTrue();
        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isTrue();
    }

    private void deleteDefaultVolumesDir() {
        FileUtil.deleteDir(DEFAULT_INDEX_VOLUME_PATH);
        FileUtil.deleteDir(DEFAULT_STREAM_VOLUME_PATH);
        FileUtil.deleteDir(DEFAULT_VOLUMES_PATH);
    }

    private static class MockVolumeService extends VolumeServiceImpl {
        private List<VolumeEntity> volumeList = null;
        private boolean saveCalled;
        private List<VolumeEntity> savedVolumes = new ArrayList<>();

        MockVolumeService(final StroomEntityManager stroomEntityManager,
                          final Security security,
                          final EntityManagerSupport entityManagerSupport,
                          final NodeCache nodeCache,
                          final VolumeConfig volumeConfig,
                          final Optional<InternalStatisticsReceiver> optionalInternalStatisticsReceiver) {
            super(stroomEntityManager,
                    security,
                    entityManagerSupport,
                    nodeCache,
                    volumeConfig,
                    optionalInternalStatisticsReceiver);
        }

        @Override
        public BaseResultList<VolumeEntity> find(final FindVolumeCriteria criteria) {
            ensureDefaultVolumes();
            return BaseResultList.createUnboundedList(volumeList);
        }

        @Override
        VolumeState saveVolumeState(final VolumeState volumeState) {
            return volumeState;
        }

        @Override
        public VolumeEntity save(VolumeEntity entity) {
            super.save(entity);
            saveCalled = true;
            savedVolumes.add(entity);
            return entity;
        }
    }
}
