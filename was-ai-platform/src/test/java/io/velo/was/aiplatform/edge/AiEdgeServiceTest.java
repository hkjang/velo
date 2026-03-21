package io.velo.was.aiplatform.edge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEdgeServiceTest {

    @Test
    void registerAndListDevices() {
        AiEdgeService service = new AiEdgeService();
        service.register("edge-01", "Edge Device 1", "raspberry-pi", 512);
        service.register("edge-02", "Edge Device 2", "jetson-nano", 2048);

        List<AiEdgeDevice> devices = service.listDevices();
        assertEquals(2, devices.size());
        assertEquals("edge-01", devices.get(0).deviceId());
        assertEquals("ONLINE", devices.get(0).status());
    }

    @Test
    void deployModelToDevice() {
        AiEdgeService service = new AiEdgeService();
        service.register("edge-01", "Edge 1", "generic", 1024);

        AiEdgeDevice device = service.deploy("edge-01", "llm-edge", "v1");
        assertEquals("llm-edge", device.deployedModel());
        assertEquals("v1", device.deployedVersion());
    }

    @Test
    void heartbeatUpdatesStatus() {
        AiEdgeService service = new AiEdgeService();
        service.register("edge-01", "Edge 1", "generic", 512);

        AiEdgeDevice device = service.heartbeat("edge-01");
        assertNotNull(device);
        assertEquals("ONLINE", device.status());
    }

    @Test
    void unknownDeviceThrowsException() {
        AiEdgeService service = new AiEdgeService();
        assertThrows(Exception.class, () -> service.heartbeat("nonexistent"));
    }
}
