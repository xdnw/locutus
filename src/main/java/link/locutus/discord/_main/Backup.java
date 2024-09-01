package link.locutus.discord._main;

import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.TimeUtil;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Backup {

    public static void backup() {
        int turnsCheck = Settings.INSTANCE.BACKUP.TURNS;
        String script = Settings.INSTANCE.BACKUP.SCRIPT;
        if (script.isEmpty()) {
            Logg.text("""
                    No backup script is mentioned in the config.yaml
                    It is recommended to set a backup script to prevent data loss
                    e.g. <https://restic.net/>
                    "- Windows Example: <https://gist.github.com/xdnw/a966c4bfe4bf2e1b9fa99ab189d1c41f>
                    "- Linux Example: <https://gist.github.com/xdnw/2b3939395961fb4108ab13fe07c43711>""");
            return;
        }
        try {
            backup(script, turnsCheck);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void backup(String script, int turnDiffRequired) throws IOException {
        if (script.isEmpty()) return;
        long currentTurn = TimeUtil.getTurn();
        File lastBackup = new File("lastBackup.txt");

        boolean shouldBackup = false;
        if (!lastBackup.exists()) {
//            lastBackup.createNewFile();
            if (!lastBackup.createNewFile()) {
                Logg.text("Failed to create lastBackup.txt");
                return;
            }
            shouldBackup = true;
        } else {
            try (DataInputStream dis = new DataInputStream(new FileInputStream(lastBackup))) {
                long lastTurn = dis.readLong();
                if (currentTurn - lastTurn >= turnDiffRequired) {
                    shouldBackup = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                // handle exception
            }
        }

        if (shouldBackup) {
            try {
                Process process = Runtime.getRuntime().exec(script);
                printProcessOutput(process);
                process.waitFor();
                writeLastBackup(lastBackup, currentTurn);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                // handle exception
            }
        }
    }

    private static void printProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Logg.text(line);
            }
        } catch (IOException e) {
            // handle exception
        }
    }

    private static void writeLastBackup(File lastBackupFile, long turn) {

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(lastBackupFile))) {
            dos.writeLong(turn);
        } catch (IOException e) {
            e.printStackTrace();
            // handle exception
        }
    }
}
