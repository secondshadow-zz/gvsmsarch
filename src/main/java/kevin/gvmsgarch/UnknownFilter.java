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
class UnknownFilter implements ContactFilter {

    public UnknownFilter() {
    }

    @Override
    public boolean process(JSONObject obj) {
        boolean retval = true;
        if (obj.has("phoneNumber")) {
            try {
                retval = obj.getString("phoneNumber").trim().matches("^Unknown\\..+");
            } catch (Exception ex) {
                retval = false;
            }
        }
        return retval;
    }
    
    @Override
    public String toString() {
        return "Unknown numbers";
    }
}
