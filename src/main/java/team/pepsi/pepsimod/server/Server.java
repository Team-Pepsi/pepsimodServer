package team.pepsi.pepsimod.server;

import team.pepsi.pepsimod.common.ClientAuthInfo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Server {
    public static HashMap<String, byte[]> pepsimod = new HashMap<String, byte[]>();
    public static Timer timer = new Timer();

    public static void main(String[] args) {
        try {
            populateArray(args[0]); //path to pepsimod jar
            ServerSocket socket = new ServerSocket(48273);
            while (true) {
                System.out.println("[Main thread] Waiting for client...");
                final Socket clientSocket = socket.accept();
                System.out.println("[Main thread] Accepted client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + ", passing to processing thread");
                schedule(() -> {
                    try {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                        Object obj = in.readObject();
                        ClientAuthInfo info;
                        if (obj instanceof ClientAuthInfo)  {
                            info = (ClientAuthInfo) obj;
                        } else {
                            System.out.println("OBJ WAS NOT INSTANCE OF CLIENTAUTHINFO!");
                            System.out.println(obj.getClass().getCanonicalName());
                            return;
                        }
                        //TODO: do some auth
                        //TODO: encrypt classes before sending
                        out.writeObject(pepsimod);
                        out.flush();

                        out.close();
                        in.close();
                        clientSocket.close();
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }, 10);
            }
        } catch (IOException e) {
            e.printStackTrace();
            //WTF JAVA!
        }
    }

    public static void populateArray(String jarName) {
        try {
            JarFile jarFile = new JarFile(new File(jarName));

            List<JarEntry> list = Collections.list(jarFile.entries());
            for (JarEntry entry : list) {
                if (entry == null) {
                    break;
                }
                if (entry.getName().endsWith(".class")) {
                    byte[] clazz = Zlib.deflate(getBytes(jarFile.getInputStream(entry)), 7);
                    String className = entry.getName().replace('/', '.');
                    className = className.substring(0, className.length() - ".class".length());
                    pepsimod.put(className, clazz);
                    System.out.println("Adding class " + className + ", byte size: " + clazz.length);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] getBytes(InputStream is) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[0xFFFF];
            for (int len; (len = is.read(buffer)) != -1; )
                os.write(buffer, 0, len);
            os.flush();
            return os.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    public static TimerTask schedule(final Runnable r, long delay) {
        final TimerTask task = new TimerTask() {
            public void run() {
                r.run();
            }
        };
        timer.schedule(task, delay);
        return task;
    }
}
