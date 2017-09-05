package team.pepsi.pepsimod.server;

import java.io.Serializable;

public class User implements Serializable {
    public String username = null;
    public String password = null;
    public String[] hwids = new String[]{null, null};

    public boolean isValidHWID(String hwid) {
        if (isHWIDSlotFree()) {
            return true;
        }

        for (String s : hwids) {
            if (hwid.equals(s)) {
                return true;
            }
        }

        return false;
    }

    public boolean isHWIDSlotFree() {
        for (String s : hwids) {
            if (s == null) {
                return true;
            }
        }

        return false;
    }

    public void addHWID(String hwid) {
        for (int i = 0; i < hwids.length; i++) {
            if (hwids[i] == null) {
                hwids[i] = hwid;
                return;
            }
        }
    }
}
