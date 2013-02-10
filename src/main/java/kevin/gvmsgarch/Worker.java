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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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

    public static enum ListLocation {

        inbox("https://www.google.com/voice/inbox/recent/inbox/"),
        history("https://www.google.com/voice/inbox/recent/all/"),
        voicemail("https://www.google.com/voice/inbox/recent/voicemail/"),
        sms("https://www.google.com/voice/inbox/recent/sms/"),
        trash("https://www.google.com/voice/inbox/recent/trash") {
            @Override
            public boolean isModeAllowed(ArchiveMode mode) {
                return mode.equals(ArchiveMode.deleteForever) || mode.equals(ArchiveMode.undelete);
            }
        };
        private String uri;

        private ListLocation(String uri) {
            assert uri != null && !uri.isEmpty();
            this.uri = uri;
        }

        public String getUri() {
            return this.uri;
        }

        public boolean isModeAllowed(ArchiveMode mode) {
            return true;
        }

        public ArchiveMode[] getAllowedModes() {
            ArrayList<ArchiveMode> modes = new ArrayList<ArchiveMode>();

            for (ArchiveMode m : ArchiveMode.values()) {
                if (isModeAllowed(m)) {
                    modes.add(m);
                }
            }
            return modes.toArray(new ArchiveMode[0]);
        }
    }

    public static enum ArchiveMode {

        archive("archiveMessages", "archive"),
        trash("deleteMessages", "trash"),
        undelete("deleteMessages", "trash"),
        markRead("mark", "read", "mark read"),
        markUnread("mark", "read","0", "mark unread"),
        deleteForever("deleteForeverMessages", "", "unrecoverably delete") {
            @Override
            public String getWarning() {
                return "DANGER DANGER DANGER DANGER DANGER DANDER DANDER\n"
                        + "WARNING: This is permanent and cannot be undone!\n"
                        + "DANGER DANGER DANGER DANGER DANGER DANDER DANDER";
            }
        };
        private String uriFragment;
        private String prettyString;
        private String labelString;
        private String labelStringValue;

        private ArchiveMode(String uriFragment, String labelString) {
            init(uriFragment, labelString, "1", this.name());
        }

        private ArchiveMode(String uriFragment, String labelString, String prettyString) {
            init(uriFragment, labelString, "1", prettyString);
        }
        
        private ArchiveMode(String uriFragment, String labelString,String labelStringValue, String prettyString) {
            init(uriFragment, labelString, labelStringValue, prettyString);
        }

        //Because apparently constructor chaining isn't allowed in enums :/
        private void init(String uriFragment, String labelString, String labelStringValue, String prettyString) {
            assert uriFragment != null && !uriFragment.isEmpty() : "Invalid mode arg";
            assert prettyString != null && !prettyString.isEmpty();
            assert labelString != null;
            assert labelStringValue!=null && !labelStringValue.isEmpty():"Invalid label string value";
            this.uriFragment = uriFragment;
            this.prettyString = prettyString;
            this.labelString = labelString;
            this.labelStringValue = labelStringValue;
        }

        public String getUriFragment() {
            return uriFragment;
        }

        public String toPrettyString() {
            return prettyString;
        }

        public String getWarning() {
            return null;
        }

        @Override
        public String toString() {
            return toPrettyString();
        }

        public String labelString() {
            return labelString;
        }
        
        public String labelStringValue() {
            return labelStringValue;
        }
    }
    private String authToken;
    private String rnrse;
    private ProgressMonitor pm;
    private ArchiveMode mode;
    private ListLocation location;
    private ContactFilter filter;

    public Worker(String authToken, String rnrse, ProgressMonitor pm, ArchiveMode mode, ListLocation location, ContactFilter filter) {
        assert authToken != null && !authToken.isEmpty() : "Invalid auth token";
        assert rnrse != null && !authToken.isEmpty() : "Invalid rnrse";
        assert pm != null : "invalid progress monitor";
        assert mode != null : "invalid operation mode";
        assert location != null : "invalid list location";
        this.authToken = authToken;
        this.rnrse = rnrse;
        this.pm = pm;
        this.mode = mode;
        this.location = location;
        this.filter = (filter == null ? new NullFilter() : filter);

        if (!location.isModeAllowed(mode)) {
            throw new IllegalArgumentException("Invalid operation mode selection for source location.");
        }
    }

    @Override
    protected Object doInBackground() throws Exception {
        archiveAll(authToken, rnrse);
        return null;
    }

    private void archiveAll(String authToken, String rnrse) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, JSONException {
        try {
            Collection<String> msgIds = Collections.EMPTY_LIST;
            int page = 1;
            int processed = 0;
            HashSet<String> alreadyProcessed = new HashSet<String>();
            do {
                int numParsed=0;
                do {
                    String json = App.extractInboxJson(authToken, this.location, page);
                    msgIds = getMessageIds(json, this.filter);
                    if (msgIds != null) {
                        numParsed+=msgIds.size();
                        msgIds.removeAll(alreadyProcessed);
                        if(msgIds.isEmpty()) {
                            page++;
                        }
                    } 
                } while (msgIds!=null && msgIds.isEmpty());
                
                if (msgIds != null && msgIds.size() > 0) {
                    
                    processed += msgIds.size();
                    archiveThese(authToken, rnrse, msgIds, mode);
                    alreadyProcessed.addAll(msgIds);
                    this.firePropertyChange("progress", null, processed);
                }
            } while (msgIds != null && msgIds.size() > 0 && !pm.isCanceled());
            this.firePropertyChange("finish", null, null);
        } catch (Exception ex) {
            this.firePropertyChange("error", null, ex);
        }
    }

    private static Set<String> getMessageIds(String textContent, ContactFilter filter) throws JSONException {
        HashSet<String> retval = new HashSet<String>();
        JSONObject top = new JSONObject(textContent);


        JSONObject messages = top.getJSONObject("messages");
        Iterator i = messages.keys();
        if (!i.hasNext()) {
            retval = null;
        } else {
            while (i.hasNext()) {
                retval.add(i.next().toString());
            }
        }



        if (retval != null) {
            HashSet<String> keysToRemove = new HashSet<String>();
            for (String key : retval) {
                if (!filter.process(messages.getJSONObject(key))) {
                    keysToRemove.add(key);
                }
            }

            retval.removeAll(keysToRemove);
        }
        return retval;
    }

    private static void archiveThese(String authToken, String rsrse, Collection<String> msgIds, ArchiveMode mode) throws HttpException, IOException {

        HttpClient c = new HttpClient();
        PostMethod m = new PostMethod("https://www.google.com/voice/inbox/" + mode.getUriFragment() + "/");
        for (String msgid : msgIds) {
            m.addParameter("messages", msgid);
        }
        if (!mode.labelString().trim().isEmpty()) {
            m.addParameter(mode.labelString(), mode.labelStringValue());
        }
        m.addParameter("_rnr_se", rsrse);
        m.setRequestHeader("Authorization", "GoogleLogin auth=" + authToken);
        int rcode;
        rcode = c.executeMethod(m);
        if (rcode != 200) {
            throw new RuntimeException("Received rcode: " + rcode);
        }
    }
}
