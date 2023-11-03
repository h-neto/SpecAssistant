package pt.haslab.specassistant.data.aggregation;

import lombok.Data;

import java.util.List;

@Data
public class IdListLong {
    String id;
    List<EntityStringLong> list;
}
