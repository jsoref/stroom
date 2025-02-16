/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.solr.search;

import stroom.docref.DocRef;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.search.api.EventRef;

class EventCoprocessorSettings implements CoprocessorSettings {
    private static final long serialVersionUID = -4916050910828000494L;

    private EventRef minEvent;
    private EventRef maxEvent;
    private long maxStreams;
    private long maxEvents;
    private long maxEventsPerStream;

    EventCoprocessorSettings() {
    }

    EventCoprocessorSettings(final EventRef minEvent, final EventRef maxEvent, final long maxStreams,
                             final long maxEvents, final long maxEventsPerStream) {
        this.minEvent = minEvent;
        this.maxEvent = maxEvent;
        this.maxStreams = maxStreams;
        this.maxEvents = maxEvents;
        this.maxEventsPerStream = maxEventsPerStream;
    }

    EventRef getMinEvent() {
        return minEvent;
    }

    EventRef getMaxEvent() {
        return maxEvent;
    }

    long getMaxStreams() {
        return maxStreams;
    }

    long getMaxEvents() {
        return maxEvents;
    }

    long getMaxEventsPerStream() {
        return maxEventsPerStream;
    }

    @Override
    public boolean extractValues() {
        return false;
    }

    @Override
    public DocRef getExtractionPipeline() {
        return null;
    }
}
