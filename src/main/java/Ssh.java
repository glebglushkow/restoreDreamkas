import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SCPClient;
import ch.ethz.ssh2.Session;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Ssh {

    private String m_ip;
    private static final String password = "root";
    private JTextArea logArea;

    //конструктор по-умолчанию
    public Ssh(String m_ip, JTextArea logArea) {
        this.m_ip = m_ip;
        this.logArea = logArea;
    }

    public boolean isDreamkasF() {
        Connection conn = null;
        Session sess = null;
        String request = null;
        try {
            conn = new Connection(m_ip);
            conn.connect(null, 100, 0);
            boolean isAuth = conn.authenticateWithPassword("root", password);
            if (isAuth == false) {
                throw new IOException("Authentication failed.");
            }

            sess = conn.openSession();
            sess.execCommand("test -e /FisGo/punix && echo 1 || echo 0");
            InputStream inp = sess.getStdout();
            InputStreamReader reader = new InputStreamReader(inp);
            BufferedReader br = new BufferedReader(reader);
            System.out.println();
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                request = line;
               // logArea.append(line + "\n");
            }

            sess.close();
            conn.close();
        } catch (IOException ex) {
            System.out.println(ex.toString());
            if (conn != null) {
                conn.close();
            }

            if (sess != null) {
                sess.close();
            }
            return false;
        }

        return request != null && request.equals("0");
    }

    //Выполнить команду bash по ssh
    public int executeSshCommand(String command) {
        System.out.println("Executing ssh command: " + command);
        //logArea.append("Executing ssh command: " + command + "\n");
        Connection conn = null;
        Session sess = null;
        try {
            conn = new Connection(m_ip);
            conn.connect(null, 100, 0);
            boolean isAuth = conn.authenticateWithPassword("root", password);
            if (isAuth == false) {
                throw new IOException("Authentication failed.");
            }

            sess = conn.openSession();
            sess.execCommand(command);
            InputStream inp = sess.getStdout();
            InputStreamReader reader = new InputStreamReader(inp);
            BufferedReader br = new BufferedReader(reader);

            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                logArea.append(line + "\n");
            }

            sess.close();
            conn.close();
        } catch (IOException ex) {
            System.out.println(ex.toString());
            if (conn != null) {
                conn.close();
            }

            if (sess != null) {
                sess.close();
            }
            return -1;
        }
        return 0;
    }

    //Выполнить scp put
    public int executeScpPut(String path, String filename) {
        Connection conn = null;
        SCPClient scpc = null;
        try {
            conn = new Connection(m_ip);
            conn.connect();
            boolean isAuth = conn.authenticateWithPassword("root", password);
            if (isAuth == false) {
                throw new IOException("Authentication failed.");
            }

            scpc = conn.createSCPClient();
            System.out.println(path + " " + filename);
            logArea.append(path + " " + filename + "\n");
            scpc.put(filename, path);

            conn.close();
        } catch (IOException ex) {
            System.out.println(ex.toString());
            if (conn != null) {
                conn.close();
                return -1;
            }
        }
        return 0;
    }

    //Выполнить scp put
    public int executeScpPut(String path, String[] filenames) {
        Connection conn = null;
        SCPClient scpc = null;
        try {
            conn = new Connection(m_ip);
            conn.connect();
            boolean isAuth = conn.authenticateWithPassword("root", password);
            if (isAuth == false) {
                throw new IOException("Authentication failed.");
            }

            scpc = conn.createSCPClient();
            scpc.put(filenames, path);

            conn.close();
        } catch (IOException ex) {
            System.out.println(ex.toString());
            if (conn != null) {
                conn.close();
                return -1;
            }
        }
        return 0;
    }

    //Выполнить scp get
    public int executeScpGet(String localPath, String remotePath) {
        Connection conn = null;
        SCPClient scpc = null;
        try {
            conn = new Connection(m_ip);
            conn.connect();
            boolean isAuth = conn.authenticateWithPassword("root", password);
            if (isAuth == false) {
                throw new IOException("Authentication failed.");
            }

            scpc = conn.createSCPClient();
            scpc.get(remotePath, localPath);

            conn.close();
        } catch (IOException ex) {
            System.out.println(ex.toString());
            if (conn != null) {
                conn.close();
                return -1;
            }
        }
        return 0;
    }
}
