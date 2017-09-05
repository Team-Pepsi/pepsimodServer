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
}
