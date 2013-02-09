/*
    Google Voice SMS archiver
    Copyright (C) 2013  Kevin Carter

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package kevin.gvmsgarch;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

/**
 *
 * @author Kevin
 */
public class Worker extends SwingWorker {

    public static enum ArchiveMode {
    
        archive("archiveMessages"), trash("deleteMessages");
        
        private String uriFragment;
        private ArchiveMode(String uriFragment){
            assert uriFragment!=null && !uriFragment.isEmpty():"Invalid mode arg";
            this.uriFragment=uriFragment;
        }
        
        public String getUriFragment(){
            return uriFragment;
        }
    }
    
    private String authToken;
    private String rnrse;
    private ProgressMonitor pm;
    private ArchiveMode mode;
    
    public Worker(String authToken, String rnrse, ProgressMonitor pm, ArchiveMode mode){
        assert authToken!=null && !authToken.isEmpty():"Invalid auth token";
        assert rnrse!=null && !authToken.isEmpty():"Invalid rnrse";
        assert pm!=null : "invalid progress monitor";
        assert mode!=null:"invalid operation mode";
        this.authToken=authToken;
        this.rnrse=rnrse;
        this.pm=pm;
        this.mode=mode;
    }
    
    @Override
    protected Object doInBackground() throws Exception {
        archiveAll(authToken, rnrse);
        return null;
    }

    private void archiveAll(String authToken, String rnrse) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, JSONException {
        try{Collection<String> msgIds = Collections.EMPTY_LIST;
        do {
            String json=App.extractInboxJson(authToken);
            msgIds = getMessageIds(json);
            
            Integer msgsLeft=App.parseMsgsLeft(json);
            
            this.firePropertyChange("progress", null, msgsLeft);
            

            if (msgIds.size() > 0) {
                archiveThese(authToken, rnrse, msgIds, mode);
            }
        } while (msgIds.size() > 0 && !pm.isCanceled());
        this.firePropertyChange("finish", null, null);}catch(Exception ex){
            this.firePropertyChange("error", null, ex);
        }
    }

    private static Collection<String> getMessageIds(String textContent) throws JSONException {
        HashSet<String> retval = new HashSet<String>();
        JSONObject top = new JSONObject(textContent);
        

        JSONObject messages = top.getJSONObject("messages");
        Iterator i = messages.keys();
        while (i.hasNext()) {
            retval.add(i.next().toString());
        }
        return retval;
    }
    
    private static void archiveThese(String authToken, String rsrse, Collection<String> msgIds, ArchiveMode mode) throws HttpException, IOException {
        System.out.println(mode);
        System.out.println(mode.getUriFragment());
        HttpClient c = new HttpClient();
        PostMethod m = new PostMethod("https://www.google.com/voice/b/0/inbox/"+ mode.getUriFragment()+"/");
        for (String msgid : msgIds) {
            m.addParameter("messages", msgid);
        }
        m.addParameter(mode.toString(), "1");
        m.addParameter("_rnr_se", rsrse);
        m.setRequestHeader("Authorization", "GoogleLogin auth=" + authToken);
        int rcode;
        rcode = c.executeMethod(m);
        if (rcode != 200) {
            throw new RuntimeException("Received rcode: " + rcode);
        }
    }
}
