package com.newtermux.features;

import android.util.Log;
import com.termux.shared.termux.TermuxConstants;
import org.json.JSONArray;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SshProfileStore {

    private static final String TAG = "SshProfileStore";
    private static final String FILE_NAME = ".termux/ssh-profiles.json";
    private static boolean sLastLoadFailed = false;

    private static File profilesFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, FILE_NAME);
    }

    public static List<SshProfile> load() {
        return load(profilesFile());
    }

    static List<SshProfile> load(File f) {
        List<SshProfile> list = new ArrayList<>();
        sLastLoadFailed = false;
        if (!f.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                list.add(SshProfile.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            sLastLoadFailed = true;
            Log.e(TAG, "Failed to load SSH profiles from " + f.getAbsolutePath(), e);
        }
        return list;
    }

    public static void save(List<SshProfile> profiles) {
        save(profiles, profilesFile());
    }

    static void save(List<SshProfile> profiles, File f) {
        if (sLastLoadFailed && profiles.isEmpty() && f.exists()) {
            Log.w(TAG, "Refusing to overwrite existing SSH profiles after load failure");
            return;
        }
        try {
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            JSONArray arr = new JSONArray();
            for (SshProfile p : profiles) arr.put(p.toJson());
            File tmp = new File(parent, f.getName() + ".tmp");
            try (FileWriter fw = new FileWriter(tmp)) {
                fw.write(arr.toString(2));
            }
            if (!tmp.renameTo(f)) {
                try (FileWriter fw = new FileWriter(f)) {
                    fw.write(arr.toString(2));
                }
                tmp.delete();
            }
            sLastLoadFailed = false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save SSH profiles to " + f.getAbsolutePath(), e);
        }
    }
}
