/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2017 Team Pepsi
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from Team Pepsi.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: Team Pepsi), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package team.pepsi.pepsimod.server;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.commons.io.FilenameUtils;
import team.pepsi.pepsimod.common.util.Zlib;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Server {
    public static HashMap<String, HashMap<String, byte[]>> version_to_pepsimod = new HashMap<>();
    public static HashMap<String, HashMap<String, byte[]>> version_to_assets = new HashMap<>();
    public static Timer timer = new Timer();
    public static DataTag tag = new DataTag(new File(DataTag.HOME_FOLDER.getPath() + File.separatorChar + ".pepsimodaccounts.dat"));
    public static ArrayList<String> bannedIPs;
    public static LoadingCache<String, Integer> ipConnectionCount = CacheBuilder.newBuilder()
            .concurrencyLevel(4)
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Integer>() {
                public Integer load(String key) {
                    return 0;
                }
            });
    public static int protocol = 5;
    public static DiscordWebhook webhook;
    private static ChannelFuture channelFuture;

    /**
     * packet IDs:
     * ClientRequest = 0
     * ServerClose = 0
     * ServerPepsiModSend = 1
     * ServerCredentialsAccepted ) 2
     * ClientChangePassword = 1
     */

    static { //TODO: ip blocking
        removeCryptographyRestrictions();
        bannedIPs = (ArrayList<String>) tag.getSerializable("super_secret_ip_bans_for_pepsimod", new ArrayList<String>());
    }

    public static void main(String[] args) {
        populateArray("/pepsimodjars"); //path to pepsimod jars
        webhook = new DiscordWebhook();
        webhook.setTitle("pepsimod update live!");
        webhook.setURL("http://www.pepsi.team");
        webhook.setStatus(true);
        webhook.setDescription("This means that the update above is now live! Launch pepsimod to test it out.\nKeep using the same launcher unless otherwise instructed!");
        webhook.setFooter("pepsimod automatically distributes updates, you don't have to do anything different.");
        new Thread() {
            public void run() {
                System.out.println("Starting");
                EventLoopGroup group = new NioEventLoopGroup();
                try {
                    ServerBootstrap b = new ServerBootstrap();
                    b.group(group)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    ch.pipeline().addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                                    ch.pipeline().addLast("frameEncoder", new LengthFieldPrepender(4));
                                    ch.pipeline().addLast(new ReadTimeoutHandler(30), new PepsiServerHandler());
                                }
                            })
                            .option(ChannelOption.SO_BACKLOG, 128)
                            .childOption(ChannelOption.SO_KEEPALIVE, true);
                    channelFuture = b.bind(48273).sync();
                    channelFuture.channel().closeFuture().sync();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    group.shutdownGracefully();
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
        schedule(() -> {
            checkForUpdates();
        }, 5 * 60 * 1000);
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
                    System.out.println("add remove resethwid save pwd ban unban banlist stop checkupdates forceload gethwids addhwid config resetconfig");
                    break;
                case "pwd":
                    User usr = (User) tag.getSerializable(split[1]);
                    if (usr == null) {
                        System.out.println("No such user!");
                    } else {
                        System.out.println(usr.password);
                    }
                    break;
                case "ban":
                    bannedIPs.add(split[1]);
                    break;
                case "banlist":
                    for (String s : bannedIPs) {
                        System.out.print(s + " ");
                    }
                    System.out.println();
                    break;
                case "unban":
                    bannedIPs.remove(split[1]);
                    break;
                case "stop":
                    tag.save();
                    channelFuture.channel().close();
                    System.exit(0);
                case "checkupdates":
                    if (checkForUpdates()) {
                        System.out.println("Success!");
                    } else {
                        System.out.println("didn't work xd");
                    }
                    break;
                case "forceload":
                    populateArray("/pepsimodjars");
                    break;
                case "gethwids":
                    User user1 = (User) tag.getSerializable(split[1]);
                    if (user1 == null) {
                        System.out.println("No such user!");
                    } else {
                        for (int i = 0; i < user1.hwids.length; i++) {
                            String s = user1.hwids[i];
                            if (s == null) {
                                s = "null";
                            }
                            System.out.println("HWID #" + i + ": " + s);
                        }
                    }
                    break;
                case "addhwid":
                    User user2 = (User) tag.getSerializable(split[1]);
                    if (user2 == null) {
                        System.out.println("No such user!");
                    } else {
                        user2.addHWIDSlots(user2.hwids.length + 1);
                        System.out.println("Added 1 HWID slot to " + split[1]);
                    }
                    break;
                case "config":
                    User user3 = (User) tag.getSerializable(split[1]);
                    if (user3 == null) {
                        System.out.println("No such user!");
                    } else {
                        System.out.println(split[1] + "'s config: " + user3.config);
                    }
                    break;
                case "resetconfig":
                    User user4 = (User) tag.getSerializable(split[1]);
                    if (user4 == null) {
                        System.out.println("No such user!");
                    } else {
                        user4.config = "{}";
                    }
                    break;
                default:
                    System.out.println("unknown!");
            }
        }
    }

    public static void populateArray(String jarsPath) {
        try {
            File dir = new File(jarsPath);

            File[] files = dir.listFiles();
            System.out.println("loading " + files.length + " versions...");
            version_to_pepsimod.clear();
            version_to_assets.clear();
            for (File file : files) {
                System.out.println("Loading " + file.getName() + " (for MC v" + (FilenameUtils.removeExtension(file.getName()).replace("pepsimod-", "")) + ")");
                HashMap<String, byte[]> classes = new HashMap<>(), assets = new HashMap<>();
                JarFile jarFile = new JarFile(file);

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
                        classes.put(className, data);
                    } else if (entry.getName().contains(".")) {
                        assets.put(entry.getName(), data = Zlib.deflate(getBytes(jarFile.getInputStream(entry)), 7));
                    }
                }

                version_to_pepsimod.put(FilenameUtils.removeExtension(file.getName()), classes);
                version_to_assets.put(FilenameUtils.removeExtension(file.getName()), assets);
                System.out.println("Added " + classes.size() + " classes and " + assets.size() + " assets to version " + FilenameUtils.removeExtension(file.getName()));
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
        timer.schedule(task, delay, delay);
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

    public static boolean checkForUpdates() {
        File f = new File("/var/lib/jenkins/updatepepsimod.txt");
        if (f.exists()) {
            if (f.delete()) {
                System.out.println("Deleted file");
                populateArray("/pepsimodjars");
                webhook.send();
                return true;
            } else {
                System.out.println("Didn't delete file");
            }
        } else {
            System.out.println("File doesn't exist");
        }
        return false;
    }
}
