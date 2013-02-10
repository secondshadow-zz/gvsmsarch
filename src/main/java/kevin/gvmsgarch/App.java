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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Hello world!
 *
 */
public class App {
    
    public static final String filterExplanation = 
            "This is mostly an exact match filter. It checks the phone number\n"+
            "field and the display name (ie: contact name). It will make some\n" +
            "attempt to do somewhat smarter things with phone numbers if they\n"+
            "are entered as just a string of numbers with no spaces; however,\n"+
            "please keep it's relative simplicity in mind. The sole exception is\n"+
            "if you enter the string Unknown. In this case it looks for calls\n" +
            "that specifically show up as 'Unknown'.";

    public static void main(String[] args) throws HttpException, IOException, ParserConfigurationException, SAXException, XPathExpressionException, JSONException, InterruptedException {
        System.out.println("Google Voice Message Archiver");
        System.out.println("Copyright (C) 2013 Kevin Carter");

        System.out.println("This program comes with ABSOLUTELY NO WARRANTY");
        System.out.println("This is free software, and you are welcome to redistribute it");
        System.out.println("under certain conditions. See the LICENSE file or");
        System.out.println("http://www.gnu.org/licenses/gpl-3.0.txt for details");
        try {
            HttpClient c = new HttpClient();

            String userName = getUserName();
            String password = getPassword();

            int locationChosenIndex = JOptionPane.showOptionDialog(
                    null,
                    "Message source",
                    "",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    Worker.ListLocation.values(),
                    Worker.ListLocation.inbox);
            if (locationChosenIndex != JOptionPane.CLOSED_OPTION) {
                int modeChosenIndex = 0;
                Worker.ArchiveMode modeChosen = null;
                Worker.ListLocation location = Worker.ListLocation.values()[locationChosenIndex];
                Worker.ArchiveMode[] availableModes = location.getAllowedModes();
                if (availableModes.length == 1) {
                    modeChosen = availableModes[0];
                } else {
                    modeChosenIndex = JOptionPane.showOptionDialog(
                            null,
                            "Operation mode",
                            "",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            availableModes,
                            Worker.ArchiveMode.archive);
                    if (modeChosenIndex != JOptionPane.CLOSED_OPTION) {
                        modeChosen = availableModes[modeChosenIndex];
                    }
                }

                ContactFilter filter = null;
                if (modeChosenIndex != JOptionPane.CLOSED_OPTION && locationChosenIndex != JOptionPane.CLOSED_OPTION) {
                    filter = buildFilter();
                }

                if (modeChosenIndex != JOptionPane.CLOSED_OPTION && locationChosenIndex != JOptionPane.CLOSED_OPTION && filter != null && areYouSure(modeChosen, location, filter)) {
                    assert modeChosen != null : "ZOMG";
                    String authToken = getToken(userName, password);
                    String rnrse = getRnrse(authToken);


                    final ProgressMonitor pm = new ProgressMonitor(null, "Working", "", 0, App.parseMsgsLeft(extractInboxJson(authToken, location, 1)));
                    pm.setMillisToDecideToPopup(0);
                    pm.setMillisToPopup(0);

                    Worker worker = new Worker(authToken, rnrse, pm, modeChosen, location, filter);
                    worker.addPropertyChangeListener(new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (evt.getPropertyName().equals("progress")) {
                                Integer msgsLeft = (Integer) evt.getNewValue();
                                pm.setProgress(pm.getMaximum() - msgsLeft);
                            } else if (evt.getPropertyName().equals("finish")) {
                                pm.setProgress(pm.getMaximum());
                                JOptionPane.showMessageDialog(null, "Task Complete", "", JOptionPane.INFORMATION_MESSAGE);
                            } else if (evt.getPropertyName().equals("error")) {
                                pm.setProgress(pm.getMaximum());
                                ((Exception) evt.getNewValue()).printStackTrace();
                                JOptionPane.showMessageDialog(null, ((Exception) evt.getNewValue()).getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                            } else {
                                System.out.println("Unsupported");
                                System.out.println(evt);
                            }
                        }
                    });
                    pm.setProgress(0);
                    worker.execute();
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
        }
    }

//    private static String parseIt(String blah) throws SAXException, ParserConfigurationException, IOException, XPathExpressionException, JSONException {
//        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(blah.getBytes()));
//        XPathExpression xpr = XPathFactory.newInstance().newXPath().compile("/response/json");
//        NodeList nl = (NodeList) xpr.evaluate(doc, XPathConstants.NODESET);
//        Node n = nl.item(0);
//        return new JSONObject(n.getTextContent()).toString(4);
//    }
    private static String getToken(String email, String password) throws HttpException, IOException {
        HttpClient client = new HttpClient();
        String retval = null;
        PostMethod post = new PostMethod("https://google.com/accounts/ClientLogin");

        post.setParameter("accountType", "GOOGLE");
        post.setParameter("Email", email);
        post.setParameter("Passwd", password);
        post.setParameter("service", "grandcentral");
        post.setParameter("source", "gvmsgarchiver");

        int rcode = -1;
        rcode = client.executeMethod(post);
        if (rcode != 200) {
            throw new RuntimeException("Bad response code: " + rcode);
        }
        post.getResponseBodyAsString();
        BufferedReader sr = new BufferedReader(new StringReader(post.getResponseBodyAsString()));
        String line = null;
        while ((line = sr.readLine()) != null) {
            if (line.startsWith("Auth")) {
                int eq = line.indexOf("=");
                retval = line.substring(eq + 1);
            }
        }
        return retval;
    }

    static String getInboxPage(String authToken, Worker.ListLocation location, int page) throws IOException {

        HttpClient client = new HttpClient();
        String retval = null;


        GetMethod m = new GetMethod(location.getUri());
        if (page > 1) {
            m.setQueryString(new NameValuePair[]{new NameValuePair("page", "p" + page)});
        }
        m.setRequestHeader("Authorization", "GoogleLogin auth=" + authToken);
        int rcode;
        rcode = client.executeMethod(m);
        if (rcode != 200) {
            throw new RuntimeException("Received rcode: " + rcode);
        }
        retval = m.getResponseBodyAsString();
        return retval;
    }

    private static String getRnrse(String authToken) throws IOException {

        String retval = null;
        HttpClient c = new HttpClient();
        GetMethod m = new GetMethod("https://www.google.com/voice/b/0");
        m.setRequestHeader("Authorization", "GoogleLogin auth=" + authToken);

        int rcode = -1;
        rcode = c.executeMethod(m);
        if (rcode != 200) {
            throw new RuntimeException("Received rcode: " + rcode);
        }
        retval = m.getResponseBodyAsString().split("'_rnr_se':")[1].split("'")[1];
        return retval;
    }

    private static String getUserName() {
        return JOptionPane.showInputDialog("Enter your gmail address");
    }

    private static String getPassword() {
        return JOptionPane.showInputDialog("Enter your password");
    }

    private static boolean areYouSure(Worker.ArchiveMode mode, Worker.ListLocation location, ContactFilter filter) {
        String message = "Are you sure you want to " + mode.toPrettyString() + " your messages from " + location.toString() + 
                "\nfiltered by " + filter.toString() +"?";
        String warning;
        if ((warning = mode.getWarning()) != null) {
            message += "\n\n" + warning;
        }
        return JOptionPane.showConfirmDialog(null, message, "Really really sure?", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static String extractInboxJson(String authToken, Worker.ListLocation location, int page) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(App.getInboxPage(authToken, location, page).getBytes()));
        XPathExpression xpr = XPathFactory.newInstance().newXPath().compile("/response/json");
        NodeList nl = (NodeList) xpr.evaluate(doc, XPathConstants.NODESET);
        Node n = nl.item(0);
        return n.getTextContent();
    }

    static int parseMsgsLeft(String extractInboxJson) throws JSONException {
        JSONObject obj = new JSONObject(extractInboxJson);
        return Integer.parseInt(obj.getString("totalSize"));
    }

    private static ContactFilter buildFilter() {
        ContactFilter retval = null;

        int optionPaneResult;

        
//        optionPaneResult = JOptionPane.showConfirmDialog(null, "Do you want to enable filtering?", "", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        String[] options=new String[]{"Yes","No"};
        
        optionPaneResult=JOptionPane.showOptionDialog(null,"Do you want to enable filtering?" , "", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        
        if (optionPaneResult == 0) {
            JOptionPane.showMessageDialog(null, filterExplanation);
            String contactName= JOptionPane.showInputDialog("Filter String (contact display name or phone number)");
            System.out.println(contactName);
            if(contactName==null || contactName.trim().isEmpty()) {
                retval = new NullFilter();
            } else {
                if(contactName.trim().equals("Unknown")) {
                    retval= new UnknownFilter();
                } else {
                    retval=new NameNumberFilter(contactName);
                }
            }
        } else if (optionPaneResult == JOptionPane.NO_OPTION) {
            retval = new NullFilter();
            
        }

        System.out.println(retval.getClass().getName());
        return retval;
    }
}
