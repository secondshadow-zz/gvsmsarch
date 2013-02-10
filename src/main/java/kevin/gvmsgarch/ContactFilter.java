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
public interface ContactFilter {
    public boolean process(JSONObject obj);
}
