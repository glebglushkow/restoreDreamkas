import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends JDialog {
    private JPanel contentPane;
    private JTextArea logArea;
    private JButton buttonOK;
    private JTextField ipField1;
    private JTextField ipField3;
    private JTextField ipField4;
    private JTextField ipField2;
    private JRadioButton searchRadioButton;
    private JRadioButton restoreRadioButton;
    private JLabel label;
    private JRadioButton updateButton;
    private JProgressBar progressBar1;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String ip1;
    private String ip2;
    private String ip3;
    private String ip4;

    private String patchName;

    public Main() {
        contentPane = new JPanel();
        $$$setupUI$$$();
        setPreferredSize(new Dimension(400, 600));

        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        settingChoose();
        logArea.setEditable(false);
        buttonOK.addActionListener(e -> executorService.submit(() -> onOK()));

        JScrollPane scroll = new JScrollPane(contentPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        setContentPane(scroll);
    }

    private void onOK() {

        if (ipField1.getText().isEmpty() | ipField2.getText().isEmpty() | ipField3.getText().isEmpty()) {
            return;
        }

        itemsEnable(false);

        logArea.setText("");

        String ip = parseIp();

        ArrayList<String> establishedIps = new ArrayList<>();

        progressBar1.setIndeterminate(true);

        if (ip4.isEmpty()) {
            searchEstablishedIp(ip, establishedIps);
            checkAutorizationIp(establishedIps);
            clearIpFields();
            itemsEnable(true);
            progressBar1.setIndeterminate(false);
            return;
        }


        restoreCashbox(ip);

        progressBar1.setIndeterminate(false);

        clearIpFields();
        itemsEnable(true);
    }

    private void restoreCashbox(String ip) {
        Ssh ssh = new Ssh(ip, logArea);
        int success = -1;
        logArea.append("==========DREAMKAS-F SEARCHING========== \n");
        for (int j = 0; j < 20; ++j) {
            success = ssh.executeSshCommand("killall updateBackupScript.sh && killall updateCorrScript.sh");
            if (success == 0) {
                ssh.executeSshCommand("rm -r /updateBackup");
                logArea.append("==========FIND DREAMKAS-F " + ip + "========== \n");
                logArea.append("==========RESTORE SYSTEM START SUCCESS========== \n");
                ssh.executeSshCommand("killall fiscat");
                ssh.executeSshCommand("rm /FisGo/fiscat");
                ssh.executeSshCommand("sync");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (success == 0) {
            logArea.append("==========COPY PATCH==========\n");
            if (ssh.executeScpPut("/", patchName + ".gz") == 0) {
                logArea.append("==========PATCH SAVE SUCCESS==========\n");
            } else {
                logArea.append("==========PATCH SAVE FAILED!!!==========\n");
                JOptionPane.showMessageDialog(null, "FAILED!!!");
                clearIpFields();
                itemsEnable(true);
                return;
            }
            logArea.append("==========UNPACKING PATCH==========\n");
            if (ssh.executeSshCommand("gunzip /" + patchName + ".gz" + " && tar xvf /" + patchName + " -C /") == 0) {
                logArea.append("==========UNPACKING PATCH SUCCESS==========\n");
            } else {
                logArea.append("==========UNPACKING PATCH FAILED!!!==========\n");
                progressBar1.setIndeterminate(false);
                JOptionPane.showMessageDialog(null, "FAILED!!!");
                clearIpFields();
                itemsEnable(true);
                return;
            }
            ssh.executeSshCommand("sync");
            ssh.executeSshCommand("sync");
            ssh.executeSshCommand("rm /" + patchName);
            logArea.append("==========REBOOT SYSTEM==========\n");
            ssh.executeSshCommand("/sbin/reboot");
            logArea.append("==========RESTORE SYSTEM SUCCESS==========\n");
            progressBar1.setIndeterminate(false);
            JOptionPane.showMessageDialog(null, "Success");
        } else {
            logArea.append("==========RESTORE FAILED!!!==========\n");
            JOptionPane.showMessageDialog(null, "FAILED!!!");
        }

    }

    private void clearIpFields() {
        ipField1.setText("");
        ipField2.setText("");
        ipField3.setText("");
        ipField4.setText("");
    }

    private void itemsEnable(boolean val) {
        searchRadioButton.setEnabled(val);
        updateButton.setEnabled(val);
        restoreRadioButton.setEnabled(val);
        ipField1.setEnabled(val);
        ipField2.setEnabled(val);
        ipField3.setEnabled(val);
        ipField4.setEnabled(val);
        label.setEnabled(val);
        buttonOK.setEnabled(val);
    }


    private ArrayList<String> checkAutorizationIp(ArrayList<String> ipList) {

        int countThreads = getCountThreads(ipList);
        ArrayList<String> dreamkasIp = getDreamkasfIp(ipList, countThreads);

        logArea.append("======================================================= \n");
        logArea.append("FOUND: " + dreamkasIp.size() + " DREAMKAS-F \n");
        logArea.append("======================================================= \n");

        if (dreamkasIp.size() == 0) {
            logArea.append("DREAMKAS-F NOT FOUND\n");
        }

        dreamkasIp.forEach(s -> {
            System.out.println(s);
            logArea.append(s + "\n");
        });

        logArea.append("=======================  COMPLITE  ==================== \n");
        ;
        return dreamkasIp;
    }

    private ArrayList<String> getDreamkasfIp(ArrayList<String> ipList, int countThreads) {
        ArrayList<DreamkasIp> threadList = new ArrayList<>();
        int indFrom;
        int indTo;
        for (int i = 0; i < countThreads; ++i) {
            indFrom = i * 10;
            // if (indFrom <= ipList.size()) {
            if (ipList.size() - indFrom > 10) {
                indTo = 10 * (i + 1) - 1;
                System.out.println("FROM - " + indFrom + ";" + "TO - " + indTo);
                ArrayList<String> subIp = new ArrayList<>(ipList.subList(indFrom, indTo));
                threadList.add(new DreamkasIp(subIp, logArea));
            } else {
                indTo = ipList.size() - 1;
                System.out.println("FROM - " + indFrom + ";" + "TO - " + indTo);
                ArrayList<String> subIp = new ArrayList<>(ipList.subList(indFrom, ipList.size() - 1));
                threadList.add(new DreamkasIp(subIp, logArea));
            }
        }

        logArea.append("search dreamkas-f...\n");

        for (DreamkasIp thread : threadList) {
            thread.start();
        }

        for (DreamkasIp thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        ArrayList<String> dreamkasIp = new ArrayList<>();
        for (DreamkasIp ip : threadList) {
            dreamkasIp.addAll(ip.getDreamkasIps());
        }

        ArrayList<String> dreamkasFip = new ArrayList<>();
        for (String ip : dreamkasIp) {
            Ssh ssh = new Ssh(ip, logArea);
            if (ssh.isDreamkasF()) {
                dreamkasFip.add(ip);
            }
        }

        return dreamkasFip;
    }

    private int getCountThreads(ArrayList<String> ipList) {
        int countThreads = ipList.size() / 10;
        if ((ipList.size() % 10 != 0) || (ipList.size() < 10)) {
            countThreads++;
        }
        return countThreads;
    }


    private void searchEstablishedIp(String ip, ArrayList<String> estIps) {
        System.out.println("=======================================================");
        logArea.append("=======================================================\n");
        System.out.println("SCAN START");
        logArea.append("SCAN START\n");
        System.out.println("=======================================================");
        logArea.append("=======================================================\n");
        ArrayList<ListIp> listIpThread = new ArrayList<>();

        for (int i = 0; i < 32; i++) {
            listIpThread.add(new ListIp(ip, i * 8, (i + 1) * 8));
            listIpThread.get(i).start();
        }

        logArea.append("scanning...\n");

        for (int i = 0; i < 32; i++) {
            try {
                listIpThread.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        logArea.append("SCAN FINISH\n");
        System.out.println("SCAN FINISH");
        for (int i = 0; i < 32; i++) {
            estIps.addAll(listIpThread.get(i).getEstablishedIps());
        }
        System.out.println("=======================================================");
        logArea.append("=======================================================\n");
        System.out.println("SCAN RESULTS " + estIps.size() + " CONNECTIONS ESTABLISHED");
        logArea.append("SCAN RESULTS " + estIps.size() + " CONNECTIONS ESTABLISHED\n");
        System.out.println("=======================================================");
        estIps.forEach(s -> System.out.println(s + " ESTABLISHED"));
        System.out.println("=======================================================");
    }

    private void checkHosts(String subnet, int max) throws IOException {
        int timeout = 1000;
        for (int i = 1; i < max; i++) {
            String host = subnet + "." + i;
            if (InetAddress.getByName(host).isReachable(timeout)) {
                System.out.println(host + " ESTABLISHED");
            } else {
                System.out.println(host + " LOST");
            }
        }
    }

    private void settingChoose() {
        ButtonGroup group = new ButtonGroup();
        group.add(searchRadioButton);
        group.add(restoreRadioButton);
        group.add(updateButton);

        restoreRadioButton.setSelected(true);
        patchName = "dirPatch_1_9_12.tar";

        searchRadioButton.addActionListener(e -> {
            if (searchRadioButton.isSelected()) {
                label.setText("Введите адрес сети");
                ipField4.setVisible(false);
            }
        });

        restoreRadioButton.addActionListener(e -> {
            if (restoreRadioButton.isSelected()) {
                patchName = "dirPatch_1_9_12.tar";
                label.setText("Введите IP кассы");
                ipField4.setVisible(true);
            }
        });

        updateButton.addActionListener(e -> {
            patchName = "dirPatch_1_9_12_from_1_0_1.tar";
            label.setText("Введите IP кассы");
            ipField4.setVisible(true);
        });
    }

    private String parseIp() {
        ip1 = ipField1.getText();
        ip2 = ipField2.getText();
        ip3 = ipField3.getText();
        ip4 = ipField4.getText();

        if (ip4.equals("")) {
            return ip1 + "." + ip2 + "." + ip3;
        }
        return ip1 + "." + ip2 + "." + ip3 + "." + ip4;
    }

    public static void main(String[] args) {
        Main dialog = new Main();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        contentPane.setLayout(new GridBagLayout());
        contentPane.putClientProperty("html.disable", Boolean.FALSE);
        logArea = new JTextArea();
        logArea.setCaretColor(new Color(-6571081));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(logArea, gbc);
        ipField1 = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(ipField1, gbc);
        ipField2 = new JTextField();
        ipField2.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(ipField2, gbc);
        buttonOK = new JButton();
        buttonOK.setText("ОК");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(buttonOK, gbc);
        ipField3 = new JTextField();
        ipField3.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(ipField3, gbc);
        ipField4 = new JTextField();
        ipField4.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(ipField4, gbc);
        restoreRadioButton = new JRadioButton();
        restoreRadioButton.setText("Восстановить Dreamkas-F");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPane.add(restoreRadioButton, gbc);
        label = new JLabel();
        label.setText("Введите IP кассы");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPane.add(label, gbc);
        searchRadioButton = new JRadioButton();
        searchRadioButton.setText("Поиск Dreamkas-F");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPane.add(searchRadioButton, gbc);
        updateButton = new JRadioButton();
        updateButton.setText("Обновить с 1.0.1");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPane.add(updateButton, gbc);
        progressBar1 = new JProgressBar();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(progressBar1, gbc);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
