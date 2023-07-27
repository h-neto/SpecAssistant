package pt.haslab.specassistant.repositories;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import pt.haslab.specassistant.data.models.Model;

import java.util.Collection;
import java.util.stream.Stream;

@ApplicationScoped
public class ModelRepository implements PanacheMongoRepositoryBase<Model, String> {

    public Stream<Model> streamByOriginalIn(Collection<String> originals) {
        return find("original in ?1", originals).stream();
    }

    public Stream<Model> streamByDerivationOf(Collection<String> ids) {
        return find("derivationOf in ?1", ids).stream();
    }

    public Stream<Model> streamByDerivationOfAndOriginal(String derivationOf, String original) {
        return find("derivationOf = ?1 and original = ?2", derivationOf, original).stream();
    }

    public Stream<Model> streamByOriginalAndUnSat(String original_id) {
        return find(new Document("original", original_id).append("sat", 1)).stream();
    }
}
