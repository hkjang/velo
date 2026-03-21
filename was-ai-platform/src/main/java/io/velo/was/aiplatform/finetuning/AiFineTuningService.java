package io.velo.was.aiplatform.finetuning;

import io.velo.was.aiplatform.registry.AiModelRegistrationRequest;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AiFineTuningService {

    private final AiModelRegistryService registryService;
    private final ConcurrentMap<String, MutableJob> jobs = new ConcurrentHashMap<>();
    private final Set<String> materializedJobs = ConcurrentHashMap.newKeySet();

    public AiFineTuningService(AiModelRegistryService registryService) {
        this.registryService = registryService;
    }

    public synchronized List<AiFineTuningJob> listJobs() {
        return jobs.values().stream()
                .map(this::snapshot)
                .sorted(Comparator.comparing(AiFineTuningJob::createdAtEpochMillis).reversed())
                .toList();
    }

    public synchronized AiFineTuningJob getJob(String jobId) {
        MutableJob job = jobs.get(jobId);
        if (job == null) {
            throw new NoSuchElementException("Fine-tuning job not found: " + jobId);
        }
        return snapshot(job);
    }

    public synchronized AiFineTuningJob createJob(AiFineTuningJobRequest request) {
        String baseModel = requireValue(request.baseModel(), "baseModel");
        registryService.findModel(baseModel);
        String datasetUri = requireValue(request.datasetUri(), "datasetUri");
        String tenant = request.tenant() == null || request.tenant().isBlank() ? "default" : request.tenant().trim();
        String objective = request.objective() == null || request.objective().isBlank() ? "domain-adaptation" : request.objective().trim();
        int epochs = request.epochs() <= 0 ? 3 : request.epochs();
        long now = System.currentTimeMillis();
        String jobId = "ft-" + UUID.randomUUID().toString().substring(0, 8);
        jobs.put(jobId, new MutableJob(jobId, baseModel, datasetUri, tenant, objective, epochs, now));
        return snapshot(jobs.get(jobId));
    }

    public synchronized AiFineTuningJob cancelJob(String jobId) {
        MutableJob job = jobs.get(jobId);
        if (job == null) {
            throw new NoSuchElementException("Fine-tuning job not found: " + jobId);
        }
        if (!"SUCCEEDED".equals(job.status)) {
            job.status = "CANCELLED";
            job.progressPercent = Math.min(job.progressPercent, 95);
            job.updatedAtEpochMillis = System.currentTimeMillis();
        }
        return snapshot(job);
    }

    private AiFineTuningJob snapshot(MutableJob job) {
        refresh(job);
        return new AiFineTuningJob(job.jobId, job.baseModel, job.datasetUri, job.tenant, job.objective, job.epochs,
                job.status, job.progressPercent, job.tunedModelName, job.createdAtEpochMillis, job.updatedAtEpochMillis);
    }

    private void refresh(MutableJob job) {
        if ("CANCELLED".equals(job.status)) {
            return;
        }
        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - job.createdAtEpochMillis) / 1000L);
        if (elapsedSeconds < 5) {
            job.status = "QUEUED";
            job.progressPercent = 10;
        } else if (elapsedSeconds < 15) {
            job.status = "RUNNING";
            job.progressPercent = 45;
        } else if (elapsedSeconds < 30) {
            job.status = "RUNNING";
            job.progressPercent = 82;
        } else {
            job.status = "SUCCEEDED";
            job.progressPercent = 100;
            if (job.tunedModelName == null || job.tunedModelName.isBlank()) {
                job.tunedModelName = job.baseModel + "-ft-" + job.jobId.substring(3);
            }
            materializeModel(job);
        }
        job.updatedAtEpochMillis = System.currentTimeMillis();
    }

    private void materializeModel(MutableJob job) {
        if (!materializedJobs.add(job.jobId)) {
            return;
        }
        ServerConfiguration.ModelProfile baseModel = registryService.routingModels().stream()
                .filter(model -> model.getName().equalsIgnoreCase(job.baseModel))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Base model not routable: " + job.baseModel));
        registryService.registerOrUpdate(new AiModelRegistrationRequest(
                job.tunedModelName,
                baseModel.getCategory(),
                baseModel.getProvider() + "-ft",
                "v1",
                baseModel.getLatencyTier(),
                baseModel.getLatencyMs() + 90,
                Math.min(100, baseModel.getAccuracyScore() + 4),
                false,
                true,
                "CANARY",
                "runtime"
        ));
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static final class MutableJob {
        private final String jobId;
        private final String baseModel;
        private final String datasetUri;
        private final String tenant;
        private final String objective;
        private final int epochs;
        private final long createdAtEpochMillis;
        private volatile long updatedAtEpochMillis;
        private volatile String status = "QUEUED";
        private volatile int progressPercent = 0;
        private volatile String tunedModelName = "";

        private MutableJob(String jobId, String baseModel, String datasetUri, String tenant, String objective, int epochs, long createdAtEpochMillis) {
            this.jobId = jobId;
            this.baseModel = baseModel;
            this.datasetUri = datasetUri;
            this.tenant = tenant;
            this.objective = objective;
            this.epochs = epochs;
            this.createdAtEpochMillis = createdAtEpochMillis;
            this.updatedAtEpochMillis = createdAtEpochMillis;
        }
    }
}