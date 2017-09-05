package team.pepsi.pepsimod.server;

import team.pepsi.pepsimod.common.util.Zlib;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Server {
    public static HashMap<String, byte[]> pepsimod = new HashMap<>();
    public static HashMap<String, byte[]> assets = new HashMap<>();
    public static Timer timer = new Timer();
    public static DataTag tag = new DataTag(new File(DataTag.HOME_FOLDER.getPath() + File.separatorChar + ".pepsimodaccounts.dat"));

    static {
        removeCryptographyRestrictions();
    }

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
                        new ClientThread(clientSocket).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        schedule(() -> {
            for (Iterator<Map.Entry<String, Serializable>> iterator = tag.objs.entrySet().iterator(); iterator.hasNext(); ) {
                if (iterator.next().getValue() == null) {
                    iterator.remove();
                }
            }
            tag.save();
        }, 60 * 60 * 1000);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine().trim();
            String[] split = input.split(" ");
            switch (split[0]) {
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
                    ((User) tag.getSerializable(split[1])).hwids = new String[]{null, null};
                    break;
                case "save":
                    tag.save();
                    break;
                case "help":
                    System.out.println("add remove resethwid save");
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
                } else if (entry.getName().contains(".")) {
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

    private static void removeCryptographyRestrictions() {
        if (!isRestrictedCryptography()) {
            System.out.println("Cryptography restrictions removal not needed");
            return;
        }
        try {
        /*
         * Do the following, but with reflection to bypass access checks:
         *
         * JceSecurity.isRestricted = false;
         * JceSecurity.defaultPolicy.perms.clear();
         * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
         */
            final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
            final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
            final Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

            final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
            isRestrictedField.setAccessible(true);
            final Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(isRestrictedField, isRestrictedField.getModifiers() & ~Modifier.FINAL);
            isRestrictedField.set(null, false);

            final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
            defaultPolicyField.setAccessible(true);
            final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

            final Field perms = cryptoPermissions.getDeclaredField("perms");
            perms.setAccessible(true);
            ((Map<?, ?>) perms.get(defaultPolicy)).clear();

            final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            defaultPolicy.add((Permission) instance.get(null));

            System.out.println("Successfully removed cryptography restrictions");
        } catch (final Exception e) {
            System.out.println("Failed to remove cryptography restrictions");
        }
    }

    private static boolean isRestrictedCryptography() {
        // This matches Oracle Java 7 and 8, but not Java 9 or OpenJDK.
        final String name = System.getProperty("java.runtime.name");
        final String ver = System.getProperty("java.version");
        return name != null && name.equals("Java(TM) SE Runtime Environment")
                && ver != null && (ver.startsWith("1.7") || ver.startsWith("1.8"));
    }
}
