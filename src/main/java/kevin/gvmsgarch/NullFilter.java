/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package kevin.gvmsgarch;

import org.json.JSONObject;

/**
 *
 * @author Kevin
 */
public class NullFilter implements ContactFilter{

    @Override
    public boolean process(JSONObject obj) {
        return true;
    }
    @Override
    public String toString() {
        return "nothing";
    }
}
