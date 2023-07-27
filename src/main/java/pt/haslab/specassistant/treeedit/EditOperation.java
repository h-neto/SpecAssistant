package pt.haslab.specassistant.treeedit;

public class EditOperation {

    public String type;

    public EditData value, target; // "value" is not used on deletions

    public EditOperation(String type, EditData value, EditData target) {
        this.type = type;
        this.value = value;
        this.target = target;
    }

    public String type() {
        return type;
    }

    public EditData value() {
        return value;
    }

    public EditData target() {
        return target;
    }

    //The development of the oracle was abandoded this method is obsulete
    public String getPattern() {
        return "";
    }

    @Override
    public String toString() {
        return "{\"type\"=\"" + type + (value != null ? ("\",\"value\"=\"" + value) : "") + "\",\"target\"=\"" + target + "\"}";
    }
}
