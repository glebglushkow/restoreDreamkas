import javax.swing.*;
import java.util.ArrayList;

public class DreamkasIp extends Thread {

    private ArrayList<String> estIp;
    private ArrayList<String> dreamkasIps;
    private JTextArea logArea;

    DreamkasIp(ArrayList<String> estIp, JTextArea logArea){
        this.estIp = estIp;
        dreamkasIps = new ArrayList<>();
        this.logArea = logArea;
    }

    public ArrayList<String> getDreamkasIps() {
        return dreamkasIps;
    }

    @Override
    public void run() {
        checkAuth(estIp);
    }

    private void checkAuth(ArrayList<String> ipList) {
        for (String ip : ipList) {
            System.out.println("TEST IP: " + ip);
            Ssh ssh = new Ssh(ip, logArea);

            if (ssh.executeSshCommand("ifconfig") == 0) {
                System.out.println("DREAMKAS-F FOUND, IP: " + ip);
                dreamkasIps.add(ip);
            }
        }
    }
}
