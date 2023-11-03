package pt.haslab.specassistant.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import pt.haslab.specassistant.services.*;

class SpecassistantProcessor {

    private static final String FEATURE = "specassistant";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem additionalBeans() {
        return new AdditionalBeanBuildItem(GraphManager.class, GraphIngestor.class, PolicyManager.class, HintGenerator.class, SpecAssistantTestService.class);
    }
}
