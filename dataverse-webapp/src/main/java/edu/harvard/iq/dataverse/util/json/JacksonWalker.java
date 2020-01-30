package edu.harvard.iq.dataverse.util.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.util.DatasetFieldWalker;

import javax.json.JsonObjectBuilder;
import java.util.Deque;
import java.util.LinkedList;

import static edu.harvard.iq.dataverse.util.json.JsonPrinter.typeClassString;

public class JacksonWalker implements DatasetFieldWalker.Listener {

    ObjectMapper objectMapper = new ObjectMapper();
    Deque<ObjectNode> objectStack = new LinkedList<>();
    Deque<ArrayNode> valueArrStack = new LinkedList<>();
    JsonObjectBuilder result = null;

    public JacksonWalker(ArrayNode valueArrStack) {
        valueArrStack.add(valueArrStack);
    }

    @Override
    public void startField(DatasetField f) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectStack.push(objectNode);
        // Invariant: all values are multiple. Diffrentiation between multiple and single is done at endField.
        valueArrStack.push(objectMapper.createArrayNode());

        DatasetFieldType typ = f.getDatasetFieldType();
        objectNode.put("typeName", typ.getName());
        objectNode.put("multiple", typ.isAllowMultiples());
        objectNode.put("typeClass", typeClassString(typ));
    }

    @Override
    public void endField(DatasetField f) {
        ObjectNode jsonField = objectStack.pop();
        ArrayNode jsonValues = valueArrStack.pop();
        if (!(jsonValues.size() == 0)) {
            jsonField.set("value",
                          f.getDatasetFieldType().isAllowMultiples() ? jsonValues
                                  : jsonValues.get(0));

            valueArrStack.peek().add(jsonField);
        }
    }

    @Override
    public void primitiveValue(DatasetField dsfv) {
        if (dsfv.getValue() != null) {
            valueArrStack.peek().add(dsfv.getValue());
        }
    }

    @Override
    public void controledVocabularyValue(ControlledVocabularyValue cvv) {
        valueArrStack.peek().add(cvv.getStrValue());
    }

    @Override
    public void startChildField(DatasetField dsfcv) {
        valueArrStack.push(objectMapper.createArrayNode());
    }

    @Override
    public void endChildField(DatasetField dsfcv) {
        ArrayNode jsonValues = valueArrStack.pop();
        if (!(jsonValues.size() == 0)) {
            ObjectNode jsonField = objectMapper.createObjectNode();

            jsonValues.forEach(jsonNode -> {
                jsonField.set(jsonNode.get("typeName").asText(), jsonNode);
            });

            valueArrStack.peek().add(jsonField);
        }
    }
}
