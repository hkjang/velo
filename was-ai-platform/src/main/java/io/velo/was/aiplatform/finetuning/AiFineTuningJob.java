package io.velo.was.aiplatform.finetuning;

public record AiFineTuningJob(
        String jobId,
        String baseModel,
        String datasetUri,
        String tenant,
        String objective,
        int epochs,
        String status,
        int progressPercent,
        String tunedModelName,
        long createdAtEpochMillis,
        long updatedAtEpochMillis
) {
}