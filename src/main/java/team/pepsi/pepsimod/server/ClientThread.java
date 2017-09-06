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

import team.pepsi.pepsimod.common.ClientAuthInfo;
import team.pepsi.pepsimod.common.ClientChangePassword;
import team.pepsi.pepsimod.common.ServerLoginErrorMessage;
import team.pepsi.pepsimod.common.ServerPepsiModSending;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import static team.pepsi.pepsimod.server.Server.*;

public class ClientThread extends Thread {
    Socket clientSocket;

    public ClientThread(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
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
            System.out.println(info.username + " " + info.hwid);
            User user = (User) tag.getSerializable(info.username);
            if (user == null) {
                out.writeObject(new ServerLoginErrorMessage("Invalid credentials!"));
                System.out.println("Invalid user: " + info.username);
                incrementBlackList();
                throw new IllegalStateException("No such user!");
            } else {
                if (user.isValidHWID(info.hwid)) {
                    //user verified!
                    if (user.isHWIDSlotFree()) {
                        System.out.println("Adding HWID");
                        user.addHWID(info.hwid);
                    }
                } else {
                    out.writeObject(new ServerLoginErrorMessage("Invalid HWID! Ask DaPorkchop_ for a reset."));
                    out.flush();
                    incrementBlackList();
                    throw new IllegalStateException("Invalid HWID");
                }
            }
            switch (info.nextRequest) {
                case 0: //play
                    System.out.println("Sending...");
                    out.writeObject(new ServerPepsiModSending(pepsimod, assets, user.password));
                    out.flush();
                    break;
                case 1: //change password
                    out.writeObject(new ServerLoginErrorMessage("notAnError"));
                    out.flush();
                    ClientChangePassword changePassword;
                    obj = in.readObject();
                    if (obj instanceof ClientChangePassword) {
                        changePassword = (ClientChangePassword) obj;
                    } else {
                        System.out.println("OBJ WAS NOT INSTANCE OF CLIENTCHANGEPASSWORD!");
                        System.out.println(obj.getClass().getCanonicalName());
                        throw new ClassNotFoundException("Excpected ClientChangePassword");
                    }
                    user.password = changePassword.newPassword;
                    break;
            }
        } catch (IllegalStateException e) {
        } catch (IOException | ClassNotFoundException e) {
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

    public void incrementBlackList()    {
        String ip = clientSocket.getRemoteSocketAddress().toString().split(":")[0];
        if (Server.bannedIPs.contains(ip))    {
            throw new IllegalStateException("Banned IP!");
        }

        int currentLogInCount = Server.ipConnectionCount.getUnchecked(ip) + 1;
        Server.ipConnectionCount.put(ip, currentLogInCount);
        if (currentLogInCount > 15) { //permabeaned
            Server.bannedIPs.add(ip);
            System.out.println("Automatically banned address: " + ip);
            throw new IllegalStateException("Banned IP!");
        }
    }
}
