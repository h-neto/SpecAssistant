package pt.haslab.specassistant.repositories;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.models.SpecAssistantTest;

@ApplicationScoped
public class TestRepository implements PanacheMongoRepositoryBase<SpecAssistantTest, SpecAssistantTest.ID> {

    public SpecAssistantTest findOrCreate(SpecAssistantTest.ID id) {
        return findByIdOptional(id).orElseGet(() -> new SpecAssistantTest(id));
    }

    public void updateOrCreate(SpecAssistantTest.ID id, ObjectId graph_id, SpecAssistantTest.Data data) {
        findOrCreate(id).setData(data).setGraphId(graph_id).persistOrUpdate();
    }

    public void deleteTestsByType(String type) {
        delete(new Document("_id.type", type));
    }

    public void deleteTestsByNotType(String type) {
        delete(new Document("_id.type", new Document("$ne", type)));
    }
}
