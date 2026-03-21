package io.velo.was.aiplatform.finetuning;

public record AiFineTuningJobRequest(
        String baseModel,
        String datasetUri,
        String tenant,
        String objective,
        int epochs
) {
}