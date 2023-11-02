package pt.haslab.specassistant.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import pt.haslab.specassistant.runtime.services.*;

@SuppressWarnings("unused")
class SpecAssistantProcessor {

    private static final String FEATURE = "specassistant";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem createGraphManager() {
        return new AdditionalBeanBuildItem(GraphManager.class);
    }


    @BuildStep
    AdditionalBeanBuildItem createGraphIngestor() {
        return new AdditionalBeanBuildItem(GraphIngestor.class);
    }

    @BuildStep
    AdditionalBeanBuildItem createPolicyManager() {
        return new AdditionalBeanBuildItem(PolicyManager.class);

    }

    @BuildStep
    AdditionalBeanBuildItem createHintGenerator() {
        return new AdditionalBeanBuildItem(HintGenerator.class);
    }

    @BuildStep
    AdditionalBeanBuildItem createSpecAssistantUtil() {
        return new AdditionalBeanBuildItem(SpecAssistantUtil.class);
    }

}
