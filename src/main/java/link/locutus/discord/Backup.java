package link.locutus.discord;

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
import java.io.PrintWriter;
import java.util.Scanner;

public class Backup {

    public static void backup() {
        int turnsCheck = Settings.INSTANCE.BACKUP.TURNS;
        String script = Settings.INSTANCE.BACKUP.SCRIPT;
        if (script.isEmpty()) return;
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
                System.out.println("Failed to create lastBackup.txt");
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
                System.out.println(line);
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
