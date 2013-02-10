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
class NameNumberFilter implements ContactFilter {

    private String filterString;

    public NameNumberFilter(String contactName) {
        this.filterString = contactName;
    }

    @Override
    public boolean process(JSONObject obj) {
        boolean retval = false;
        if (obj.has("phoneNumber")) {
            try {
                retval |= obj.getString("phoneNumber").trim().equals(filterString);
                if(filterString.matches("^[0-9]+$")) {
                    retval |= obj.getString("phoneNumber").trim().equals("+"+filterString);
                    if(filterString.length()==10) {                        
                        retval |= obj.getString("phoneNumber").trim().equals("1"+filterString);
                        retval |= obj.getString("phoneNumber").trim().equals("+1" +filterString);
                    }
                    
                }
            } catch (Exception ex) {
            }
        }

        if (obj.has("displayNumber")) {
            try {
                retval |= obj.getString("displayNumber").trim().equals(filterString);
            } catch (Exception ex) {
            }
        }
        return retval;
    }
    
    @Override
    public String toString() {
        return "contact filtering where the contact is " + filterString;
    }
}
