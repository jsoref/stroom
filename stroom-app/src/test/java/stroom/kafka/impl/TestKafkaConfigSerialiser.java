package stroom.kafka.impl;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.hadoop.hbase.util.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.Persistence;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class TestKafkaConfigSerialiser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKafkaConfigSerialiser.class);

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Persistence persistence;

    @Inject
    private KafkaConfigSerialiser serialiser;

    @BeforeEach
    void setUp() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();

                bind(SecurityContext.class).toInstance(securityContext);
                bind(Persistence.class).toInstance(persistence);
                install(new DocStoreModule());
            }
        });

        injector.injectMembers(this);
    }

    @Test
    void testSerialiseDeserialise() throws IOException {

        KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc();
        kafkaConfigDoc.setDescription("My description");

        kafkaConfigDoc.addProperty("Integer", 123);
        kafkaConfigDoc.addProperty("Short", (short)123);
        kafkaConfigDoc.addProperty("Long", 123L);
        kafkaConfigDoc.addProperty("BooleanTrue", true);
        kafkaConfigDoc.addProperty("BooleanFalse", false);
        kafkaConfigDoc.addProperty("Class1", Object.class);
        kafkaConfigDoc.addProperty("Class2", GoodClass.class);
        kafkaConfigDoc.addProperty("String", "A string");

        final Map<String, byte[]> data = serialiser.write(kafkaConfigDoc);

        String json = Bytes.toString(data.get("meta"));

        LOGGER.info(json);

        KafkaConfigDoc kafkaConfigDoc2 = serialiser.read(data);

        Assertions.assertThat(kafkaConfigDoc)
                .isEqualTo(kafkaConfigDoc2);

        Assertions.assertThat(kafkaConfigDoc2.getProperties().get("Class2"))
                .isInstanceOf(Class.class);
        Assertions.assertThat(((Class)kafkaConfigDoc2.getProperties().get("Class2")).getName())
                .isEqualTo(GoodClass.class.getName());
    }

    @Test
    void testGoodType() throws IOException, ClassNotFoundException {
        String json = "{\n" +
                "  \"description\" : \"My description\",\n" +
                "  \"properties\" : {\n" +
                "    \"aStringValue\" : [ \"stringType\", \"abcdefg\" ],\n" +
                "    \"aGoodClass\" : [ \"classType\", \"stroom.kafka.impl.TestKafkaConfigSerialiser$GoodClass\" ]\n" +
                "  },\n" +
                "  \"kafkaVersion\" : \"0.10.0.1\"\n" +
                "}";

        LOGGER.info("\n{}", json);

        final Map<String, byte[]> data = Map.of("meta", Bytes.toBytes(json));
        final KafkaConfigDoc kafkaConfigDoc = serialiser.read(data);

        Assertions.assertThat(kafkaConfigDoc.getProperties().get("aGoodClass"))
                .isInstanceOf(Class.class);
        Assertions.assertThat(((Class)kafkaConfigDoc.getProperties().get("aGoodClass")).getName())
                .isEqualTo(GoodClass.class.getName());
    }

    @Test
    void testBadType() throws IOException {
        String json = "{\n" +
                "  \"description\" : \"My description\",\n" +
                "  \"properties\" : {\n" +
                "    \"aStringValue\" : [ \"stringType\", \"abcdefg\" ],\n" +
                // want to make sure jackson won't try to instantiate some unknown class
                "    \"Bad\" : [ \"stroom.kafka.impl.TestKafkaConfigSerialiser.BadClass\", \"some value\" ],\n" +
                "  },\n" +
                "  \"kafkaVersion\" : \"0.10.0.1\"\n" +
                "}";

        final Map<String, byte[]> data = Map.of("meta", Bytes.toBytes(json));

        Assertions.assertThatExceptionOfType(InvalidTypeIdException.class)
                .isThrownBy(() -> {
                    serialiser.read(data);
                });
    }

    static class BadClass {
        private static final Logger LOGGER = LoggerFactory.getLogger(BadClass.class);

        BadClass() {
            LOGGER.info("Ctor called for BadClass");
        }
    }

    public static class GoodClass {
        private static final Logger LOGGER = LoggerFactory.getLogger(GoodClass.class);

        public GoodClass() {
            LOGGER.info("Ctor called for GoodClass");
        }
    }

}
