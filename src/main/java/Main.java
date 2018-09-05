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

        //проверка что поля ввода не пустые, если пустые ничего не делать
        if (ipField1.getText().isEmpty() | ipField2.getText().isEmpty() | ipField3.getText().isEmpty()) {
            return;
        }

        //заблокировать items
        itemsEnable(false);
        logArea.setText("");

        //считать ip
        String ip = parseIp();


        //запустить прогресс бар
        progressBar1.setIndeterminate(true);

        //если четвертое поле ввода ip пустое, значит запустит алгоритм поиска кассы в сети, после завершить работу метода
        if (ip4.isEmpty()) {
            ArrayList<String> establishedIps = searchEstablishedIp(ip);
            checkAutorizationIp(establishedIps);
            clearIpFields();
            itemsEnable(true);
            progressBar1.setIndeterminate(false);
            return;
        }

        //алгоритм восстановления кассы
        restoreCashbox(ip);

        //выключить прогрессбар
        progressBar1.setIndeterminate(false);

        //очистить поля и сделать доступными все контроллы
        clearIpFields();
        itemsEnable(true);
    }

    /**
     * Алгоритм восстановления кассы. Так же, если выбран радиобаттон "обновления с 1.0.1, метод обновляет кассу. Для
     * этого используется переменная String @patchName содержащая имя патча.
     * @param ip - ip-адрес кассы с которой производятся действия
     */
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

    /**
     * Метод очистки полей для ввода ip
     */
    private void clearIpFields() {
        ipField1.setText("");
        ipField2.setText("");
        ipField3.setText("");
        ipField4.setText("");
    }

    /**
     * Метод для блокирования и активации контролов
     * @param val - true или false
     */
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

    /**
     * Метод принимает на вход все ip-адреса доступные в сети и возвращает только адреса кас Дримкас-Ф
     * @param ipList - список доступныз ip
     * @return - ip-адреса дримкасов-ф
     */
    private ArrayList<String> checkAutorizationIp(ArrayList<String> ipList) {

        //рассчитать кол-во потоков
        int countThreads = getCountThreads(ipList.size());

        //получить ip адресса дримкасов-ф
        ArrayList<String> dreamkasIp = getDreamkasfIp(ipList, countThreads);

        //вывести колличество найденный адресов в лог
        logArea.append("======================================================= \n");
        logArea.append("FOUND: " + dreamkasIp.size() + " DREAMKAS-F \n");
        logArea.append("======================================================= \n");

        //если адресов нет выводим в лог сообщение о том что мы очень сожалеем позвоните нам позже
        if (dreamkasIp.size() == 0) {
            logArea.append("DREAMKAS-F NOT FOUND\n");
        }

        //вывод адресов в лог
        dreamkasIp.forEach(s -> {
            System.out.println(s);
            logArea.append(s + "\n");
        });

        logArea.append("=======================  COMPLITE  ==================== \n");
        return dreamkasIp;
    }

    /**
     * Метод принимает на вход список ip доступных в сети. В потоках пытается подключиться к каждому. Проверяет что адрес
     * к которому удалось подключиться является адресом Дримкаса-Ф. Возвращает адреса дримкаса-ф
     * @param ipList - лист с доступными адресами
     * @param countThreads - количество потоков необходимых для обрабоки данного листа
     * @return - лист с адресами кас Дримкас-ф
     */
    private ArrayList<String> getDreamkasfIp(ArrayList<String> ipList, int countThreads) {
        //разбитие ip-адресов в потоки
        ArrayList<DreamkasIp> threadList = new ArrayList<>();
        int indFrom;
        int indTo;
        for (int i = 0; i < countThreads; ++i) {
            indFrom = i * 10;
            if (ipList.size() - indFrom > 10) {
                indTo = 10 * (i + 1) - 1;
                ArrayList<String> subIp = new ArrayList<>(ipList.subList(indFrom, indTo));
                threadList.add(new DreamkasIp(subIp, logArea));
            } else {
                ArrayList<String> subIp = new ArrayList<>(ipList.subList(indFrom, ipList.size() - 1));
                threadList.add(new DreamkasIp(subIp, logArea));
            }
        }

        logArea.append("search dreamkas-f...\n");

        //старт потоков
        for (DreamkasIp thread : threadList) {
            thread.start();
        }

        //дождаться окончания всех потоков
        for (DreamkasIp thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //добавить все найденные потоками адреса в один лист
        ArrayList<String> dreamkasIp = new ArrayList<>();
        for (DreamkasIp ip : threadList) {
            dreamkasIp.addAll(ip.getDreamkasIps());
        }

        //проверка является ли каждый найденый адрес адресом дримкаса-ф
        ArrayList<String> dreamkasFip = new ArrayList<>();
        for (String ip : dreamkasIp) {
            Ssh ssh = new Ssh(ip, logArea);
            if (ssh.isDreamkasF()) {
                dreamkasFip.add(ip);
            }
        }

        return dreamkasFip;
    }

    /**
     * Метож считает количество потоков для обработки массива ip-адресов, из которых нужно найти дримкас-ф.
     * Предполагается отдавать каждому потоку по 10 адресов, исходя из этого счтается количество потоков.
     * @param sizeListIp - количество адресов.
     * @return количество потоков необходимое для обработки всего массива
     */
    private int getCountThreads(int sizeListIp) {
        int countThreads = sizeListIp / 10;
        if ((sizeListIp % 10 != 0) || (sizeListIp < 10)) {
            countThreads++;
        }
        return countThreads;
    }

    /**
     * Метод принимает на вход адрес сети в которой находится касса и находит в этой сети все доступные ip адреса
     * @param host - адрес сети в которой находится касса
     * @return лист с доступными ip адресами
     */
    private ArrayList<String> searchEstablishedIp(String host) {
        System.out.println("=======================================================");
        logArea.append("=======================================================\n");
        System.out.println("SCAN START");
        logArea.append("SCAN START\n");
        System.out.println("=======================================================");
        logArea.append("=======================================================\n");

        //создание потоков и их запуск
        ArrayList<ListIp> listIpThread = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            listIpThread.add(new ListIp(host, i * 8, (i + 1) * 8));
            listIpThread.get(i).start();
        }

        logArea.append("scanning...\n");

        //дожидаемся завершения потоков
        for (int i = 0; i < 32; i++) {
            try {
                listIpThread.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        logArea.append("SCAN FINISH\n");
        System.out.println("SCAN FINISH");

        //получить все ip полученные каждым потоком
        ArrayList<String> establishedIps = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            establishedIps.addAll(listIpThread.get(i).getEstablishedIps());
        }

        System.out.println("=======================================================");
        logArea.append("=======================================================\n");
        System.out.println("SCAN RESULTS " + establishedIps.size() + " CONNECTIONS ESTABLISHED");
        logArea.append("SCAN RESULTS " + establishedIps.size() + " CONNECTIONS ESTABLISHED\n");
        System.out.println("=======================================================");
        establishedIps.forEach(s -> System.out.println(s + " ESTABLISHED"));
        System.out.println("=======================================================");

        return establishedIps;
    }

    /**
     * Метод реализующий выбор радиобаттонов
     */
    private void settingChoose() {
        ButtonGroup group = new ButtonGroup();
        group.add(searchRadioButton);
        group.add(restoreRadioButton);
        group.add(updateButton);

        //Поставить "восстановления кассы" по умолчанию
        restoreRadioButton.setSelected(true);
        patchName = "dirPatch_1_9_12.tar";

        //листенер "поиск кассы". если баттон выбран, скрывает 4-ое поле ввода ip-адреса
        searchRadioButton.addActionListener(e -> {
            if (searchRadioButton.isSelected()) {
                label.setText("Введите адрес сети");
                ipField4.setVisible(false);
            }
        });

        //листенер "востановление кассы". активирует 4-ое поле на случай если до этого оно было скрыто
        restoreRadioButton.addActionListener(e -> {
            if (restoreRadioButton.isSelected()) {
                patchName = "dirPatch_1_9_12.tar";
                label.setText("Введите IP кассы");
                ipField4.setVisible(true);
            }
        });

        //листенер "обновление кассы". активирует 4-ое поле на случай если до этого оно было скрыто
        updateButton.addActionListener(e -> {
            patchName = "dirPatch_1_9_12_from_1_0_1.tar";
            label.setText("Введите IP кассы");
            ipField4.setVisible(true);
        });
    }

    /**
     * Метод парсит поля ввода ip адресса
     * @return
     */
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
