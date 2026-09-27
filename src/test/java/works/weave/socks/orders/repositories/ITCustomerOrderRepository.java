package works.weave.socks.orders.repositories;

import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import works.weave.socks.orders.OrderApplication;
import works.weave.socks.orders.entities.CustomerOrder;
import works.weave.socks.orders.entities.Shipment;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {OrderApplication.class, ITCustomerOrderRepository.EmbeddedMongoConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ITCustomerOrderRepository {

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @After
    public void removeOrders() {
        customerOrderRepository.deleteAll();
    }

    @Test
    public void findsAnOrderByItsEmbeddedShipmentIdThroughRepositoryAndRestSearch() {
        Shipment shipment = new Shipment("shipment-id", "shipment-name");
        CustomerOrder order = new CustomerOrder();
        order.setShipment(shipment);
        CustomerOrder savedOrder = customerOrderRepository.save(order);
        assertEquals("PLACED", savedOrder.getStatus());

        Optional<CustomerOrder> result = customerOrderRepository.findByShipmentId(shipment.getId());
        assertTrue(result.isPresent());
        assertEquals(savedOrder.getId(), result.get().getId());
        assertFalse(customerOrderRepository.findByShipmentId("unknown-shipment-id").isPresent());

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/orders/search/shipmentId?shipmentId={shipmentId}",
                String.class, shipment.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains(savedOrder.getId()));
    }

    @TestConfiguration
    @Profile("test")
    static class EmbeddedMongoConfiguration {
        private static final int PORT = findAvailablePort();

        @Bean(destroyMethod = "stop")
        public MongodExecutable embeddedMongoExecutable() throws IOException {
            IMongodConfig config = new MongodConfigBuilder()
                    .version(Version.Main.PRODUCTION)
                    .net(new Net(PORT, Network.localhostIsIPv6()))
                    .build();
            return MongodStarter.getDefaultInstance().prepare(config);
        }

        @Bean(destroyMethod = "stop")
        public MongodProcess embeddedMongoProcess(MongodExecutable executable) throws IOException {
            return executable.start();
        }

        @Bean(destroyMethod = "close")
        public MongoClient mongoClient(MongodProcess process) throws IOException {
            return new MongoClient("localhost", PORT);
        }

        private static int findAvailablePort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to find a free MongoDB port", ex);
            }
        }
    }
}
