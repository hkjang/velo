package io.velo.was.aiplatform.finetuning;

import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiFineTuningServiceTest {

    @Test
    void createsAndCancelsFineTuningJob() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiFineTuningService service = new AiFineTuningService(registryService);

        AiFineTuningJob created = service.createJob(new AiFineTuningJobRequest(
                "llm-general", "s3://datasets/support.jsonl", "tenant-a", "support", 3
        ));
        AiFineTuningJob cancelled = service.cancelJob(created.jobId());

        assertFalse(service.listJobs().isEmpty());
        assertEquals("CANCELLED", cancelled.status());
    }
}