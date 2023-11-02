package pt.haslab.specassistant.runtime.services.treeedit;

import lombok.Getter;
import pt.haslab.specassistant.runtime.data.transfer.HintMsg;

@Getter
public class EditOperation {

    public String type;

    public EditData value, target; // "value" is not used on deletions

    public EditOperation(String type, EditData value, EditData target) {
        this.type = type;
        this.value = value;
        this.target = target;
    }

    @Override
    public String toString() {
        return "{\"type\"=\"" + type + (value != null ? ("\",\"value\"=\"" + value) : "") + "\",\"target\"=\"" + target + "\"}";
    }

    public HintMsg getHintMessage() {
        return switch (this.type) {
            case "rename", "delete" -> HintMsg.from(target.position(), "Try to change this declaration");
            case "insert" -> HintMsg.from(target.position(), "Try adding something to this declaration");
            default -> null;
        };
    }
}
