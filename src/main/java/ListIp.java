import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ListIp extends Thread {

    private int min;
    private int max;
    private String subnet;
    private Ssh ssh;
    private HashSet<String> establishedIps;

    ListIp(String subnet, int min, int max) {
        this.subnet = subnet;
        this.min = min;
        this.max = max;
        establishedIps = new HashSet<>();
    }

    public HashSet<String> getEstablishedIps() {
        return establishedIps;
    }

    @Override
    public void run() {

        try {
            checkHosts(subnet, min, max);
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.run();
    }

    private void checkHosts(String subnet, int min, int max) throws IOException {
        int timeout = 1000;
        for (int j = 0; j < 4; j++) {
            for (int i = min; i < max; i++) {
                String host = subnet + "." + i;
                if (InetAddress.getByName(host).isReachable(timeout)) {
                    establishedIps.add(host);
                } else {
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
