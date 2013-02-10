/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package kevin.gvmsgarch;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;

/**
 *
 * @author Kevin
 */
class ProgressPropertyChangeListener implements PropertyChangeListener{
    private ProgressMonitor pm;
    public ProgressPropertyChangeListener(ProgressMonitor pm) {
        assert pm!=null;
        this.pm=pm;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals("progress")) {
            pm.setProgress((Integer) evt.getNewValue());
        } else if (evt.getPropertyName().equals("finish")) {
            pm.setProgress(pm.getMaximum());
            JOptionPane.showMessageDialog(null, "Task Complete", "", JOptionPane.INFORMATION_MESSAGE);
        } else if (evt.getPropertyName().equals("error")) {
            pm.setProgress(pm.getMaximum());
            ((Exception) evt.getNewValue()).printStackTrace();
            
            Exception ex = (Exception) evt.getNewValue();
            App.showErrorDialog(ex);
        }
    }
}
