package pt.haslab.specassistant.data.transfer;

import edu.mit.csail.sdg.alloy4.Pos;

public class HintMsg {
    public String msg;
    public Pos pos;

    public HintMsg() {
    }


    public static HintMsg from(Pos pos, String msg) {
        HintMsg response = new HintMsg();

        response.msg = msg;
        response.pos = pos;

        return response;
    }
}
