package team.pepsi.pepsimod.server;

import team.pepsi.pepsimod.common.*;
import team.pepsi.pepsimod.common.util.Zlib;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Server {
    public static HashMap<String, byte[]> pepsimod = new HashMap<>();
    public static HashMap<String, byte[]> assets = new HashMap<>();
    public static Timer timer = new Timer();
    public static DataTag tag = new DataTag(new File(DataTag.HOME_FOLDER.getPath() + File.separatorChar + ".pepsimodaccounts.dat"));

    public static void main(String[] args) {
        populateArray(args[0]); //path to pepsimod jar
        new Thread() {
            public void run() {
                try {
                    ServerSocket socket = new ServerSocket(48273);
                    while (true) {
                        System.out.println("[Main thread] Waiting for client...");
                        final Socket clientSocket = socket.accept();
                        System.out.println("[Main thread] Accepted client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + ", passing to processing thread");
                        new Thread() {
                            public void run() {
                                ObjectOutputStream out = null;
                                ObjectInputStream in = null;
                                try {
                                    out = new ObjectOutputStream(clientSocket.getOutputStream());
                                    in = new ObjectInputStream(clientSocket.getInputStream());
                                    Object obj = in.readObject();
                                    ClientAuthInfo info;
                                    if (obj instanceof ClientAuthInfo) {
                                        info = (ClientAuthInfo) obj;
                                    } else {
                                        System.out.println("OBJ WAS NOT INSTANCE OF CLIENTAUTHINFO!");
                                        System.out.println(obj.getClass().getCanonicalName());
                                        throw new ClassNotFoundException("Excpected ClientAuthInfo");
                                    }
                                    User user = (User) tag.getSerializable(info.username);
                                    if (user == null)   {
                                        out.writeObject(new ServerLoginErrorMessage(0));
                                        throw new IllegalStateException("No such user!");
                                    } else {
                                        if (user.password.equals(info.password))    {
                                            //user verified!
                                            if (user.hwid == null)  {
                                                //need to get HWID for encryption
                                                out.writeObject(new ServerFirstAuthMessage());
                                                out.flush();
                                                obj = in.readObject();
                                                ClientHWIDSendMessage hwidSendMessage;
                                                if (obj instanceof ClientHWIDSendMessage) {
                                                    hwidSendMessage = (ClientHWIDSendMessage) obj;
                                                } else {
                                                    System.out.println("OBJ WAS NOT INSTANCE OF CLIENTHWIDSENDMESSAGE!");
                                                    System.out.println(obj.getClass().getCanonicalName());
                                                    throw new ClassNotFoundException("Excpected ClientHWIDSendMessage");
                                                }
                                                user.hwid = hwidSendMessage.hwid;
                                            }
                                        } else {
                                            out.writeObject(new ServerLoginErrorMessage(1));
                                            out.flush();
                                            throw new IllegalStateException("INVALID CREDENTIALS");
                                        }
                                    }
                                    switch (info.nextRequest)   {
                                        case 0: //play
                                            out.writeObject(new ServerPepsiModSending(pepsimod, assets, user.hwid));
                                            out.flush();
                                            break;
                                        case 1: //change password
                                            ClientChangePassword changePassword;
                                            if (obj instanceof ClientChangePassword) {
                                                changePassword = (ClientChangePassword) obj;
                                            } else {
                                                System.out.println("OBJ WAS NOT INSTANCE OF CLIENTCHANGEPASSWORD!");
                                                System.out.println(obj.getClass().getCanonicalName());
                                                throw new ClassNotFoundException("Excpected ClientChangePassword");
                                            }
                                            user.password = changePassword.newPassword;
                                    }
                                } catch (IOException | ClassNotFoundException | IllegalStateException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (out != null) {
                                            out.close();
                                        }
                                        if (in != null) {
                                            in.close();
                                        }
                                        clientSocket.close();
                                    } catch (IOException e) {
                                        //WTF JAVA
                                    }
                                }
                            }
                        }.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        schedule(() -> {
            for (Iterator<Map.Entry<String, Serializable>> iterator = tag.objs.entrySet().iterator(); iterator.hasNext();)   {
                if (iterator.next().getValue() == null) {
                    iterator.remove();
                }
            }
            tag.save();
        }, 60 * 60 * 1000);
        Scanner scanner = new Scanner(System.in);
        while (true)    {
            String input = scanner.nextLine().trim();
            String[] split = input.split(" ");
            switch (split[0])   {
                case "add":
                    User user = new User();
                    user.username = split[1];
                    user.password = split[2];
                    tag.setSerializable(user.username, user);
                    break;
                case "remove":
                    tag.setSerializable(split[1], null);
                    break;
                case "resethwid":
                    ((User) tag.getSerializable(split[1])).hwid = null;
                    break;
                case "save":
                    tag.save();
                    break;
            }
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
                byte[] data;
                if (entry.getName().endsWith(".class")) {
                    data = Zlib.deflate(getBytes(jarFile.getInputStream(entry)), 7);
                    String className = entry.getName().replace('/', '.');
                    className = className.substring(0, className.length() - ".class".length());
                    pepsimod.put(className, data);
                    System.out.println("Adding class " + className + ", byte size: " + data.length);
                } else if (entry.getName().contains("."))   {
                    assets.put(entry.getName(), data = Zlib.deflate(getBytes(jarFile.getInputStream(entry)), 7));
                    System.out.println("Adding resource " + entry.getName() + ", byte size: " + data.length);
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
            is.close();
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
