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

import team.pepsi.pepsimod.common.*;
import team.pepsi.pepsimod.common.message.ClientboundMessage;
import team.pepsi.pepsimod.common.util.SerializableUtils;
import team.pepsi.pepsimod.server.exception.InvalidHWIDException;
import team.pepsi.pepsimod.server.exception.NoSuchUserException;
import team.pepsi.pepsimod.server.exception.UserBannedException;
import team.pepsi.pepsimod.server.exception.WrongClassException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import static team.pepsi.pepsimod.server.Server.*;

public class ClientThread extends Thread {
    public static final int
            ERROR_BANNED = 0,
            ERROR_HWID = 1,
            ERROR_WRONGCLASS = 2,
            NOTIFICATION_SUCCESS = -1,
            NOTIFICATION_USER = 1,
            NOTIFICATION_IGNORE = -2;
    Socket clientSocket;
    String ip;

    public ClientThread(Socket socket) {
        this.clientSocket = socket;
        ip = clientSocket.getRemoteSocketAddress().toString().split(":")[0];
    }

    @Override
    public void run() {
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            if (Server.bannedIPs.contains(ip)) {
                throw new UserBannedException();
            }

            in = new ObjectInputStream(clientSocket.getInputStream());
            out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerNotification(null, NOTIFICATION_IGNORE))));
            out.flush();
            Object obj = in.readObject();
            ClientAuthInfo info;
            if (obj instanceof ClientAuthInfo) {
                info = (ClientAuthInfo) obj;
            } else {
                System.out.println("OBJ WAS NOT INSTANCE OF CLIENTAUTHINFO!");
                System.out.println(obj.getClass().getCanonicalName());
                throw new WrongClassException();
            }
            System.out.println(info.username + " " + info.hwid);
            User user = (User) tag.getSerializable(info.username);
            if (user == null) {
                System.out.println("Invalid user: " + info.username);
                incrementBlackList();
                throw new NoSuchUserException();
            } else {
                if (user.isValidHWID(info.hwid)) {
                    //user verified!
                    if (user.isHWIDSlotFree()) {
                        System.out.println("Adding HWID");
                        user.addHWID(info.hwid);
                    }
                } else {
                    incrementBlackList();
                    throw new InvalidHWIDException();
                }
            }

            switch (info.nextRequest) {
                case 0: //play
                    System.out.println("Sending...");
                    out.writeObject(new ServerPepsiModSending(pepsimod, assets, user.password));
                    out.flush();
                    break;
                case 1: //change password
                    out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerNotification(null, NOTIFICATION_IGNORE))));
                    out.flush();
                    ClientChangePassword changePassword;
                    obj = in.readObject();
                    if (obj instanceof ClientChangePassword) {
                        changePassword = (ClientChangePassword) obj;
                    } else {
                        System.out.println("OBJ WAS NOT INSTANCE OF CLIENTCHANGEPASSWORD!");
                        System.out.println(obj.getClass().getCanonicalName());
                        throw new WrongClassException();
                    }
                    user.password = changePassword.newPassword;
                    out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerNotification("Password change successful!", NOTIFICATION_SUCCESS))));
                    break;
            }
        } catch (UserBannedException e) {
            try {
                out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerLoginErrorMessage("Banned IP!", ERROR_BANNED))));
                out.flush();
            } catch (IOException e1) {
                e.printStackTrace();
            }
        } catch (InvalidHWIDException e) {
            try {
                out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerLoginErrorMessage("Invalid HWID! Ask DaPorkchop_ for a reset!", ERROR_HWID))));
                out.flush();
            } catch (IOException e1) {
                e.printStackTrace();
            }
        } catch (NoSuchUserException e) {
            try {
                out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerNotification("No such user!", NOTIFICATION_USER))));
                out.flush();
            } catch (IOException e1) {
                e.printStackTrace();
            }
        } catch (WrongClassException | ClassNotFoundException e) {
            try {
                out.writeObject(new ClientboundMessage(false, SerializableUtils.toBytes(new ServerLoginErrorMessage("Invalid class sent!", ERROR_WRONGCLASS))));
                out.flush();
            } catch (IOException e1) {
                e.printStackTrace();
            }
        } catch (IOException e) {
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

    public void incrementBlackList() {
        int currentLogInCount = Server.ipConnectionCount.getUnchecked(ip) + 1;
        Server.ipConnectionCount.put(ip, currentLogInCount);
        if (currentLogInCount > 15) { //permabeaned
            Server.bannedIPs.add(ip);
            System.out.println("Automatically banned address: " + ip);
            throw new UserBannedException();
        }
    }

    public void resetBlacklist() {
        int currentLogInCount = Server.ipConnectionCount.getUnchecked(ip) + 1;
        Server.ipConnectionCount.put(ip, currentLogInCount);
        if (currentLogInCount > 15) { //permabeaned
            Server.bannedIPs.add(ip);
            System.out.println("Automatically banned address: " + ip);
            throw new UserBannedException();
        }
    }
}
