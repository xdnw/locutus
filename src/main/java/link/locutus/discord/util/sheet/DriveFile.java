package link.locutus.discord.util.sheet;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import link.locutus.discord.config.Settings;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

public class DriveFile {
    private static final String APPLICATION_NAME = "DriveFile";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    protected static final String TOKENS_DIRECTORY_PATH = "config" + java.io.File.separator + "tokens2";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_METADATA, DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "config" + java.io.File.separator + "credentials-drive.json";
    private final Drive service;
    private final String fileId;


    public static java.io.File getCredentialsFile() {
        return new java.io.File(CREDENTIALS_FILE_PATH);
    }

    public static boolean credentialsExists() {
        return getCredentialsFile().exists();
    }
    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials-drive.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        java.io.File file = getCredentialsFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        try (InputStream in = new FileInputStream(file)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(Settings.INSTANCE.WEB.GOOGLE_DRIVE_VALIDATION_PORT).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }

    public static Drive createService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
        return service;
    }

    public DriveFile(String fileId) throws GeneralSecurityException, IOException {
        this.service = createService();
        this.fileId = fileId;
    }

    public String getId() {
        return fileId;
    }

    public enum DriveRole {
        READER,
        WRITER,
        ORGANIZER
    }

    public void shareWithAnyone(DriveRole role) throws IOException {
        service.permissions().create(fileId, new Permission()
                .setType("anyone")
                .setRole(role.name().toLowerCase())
        ).execute();
    }


    public void shareWithEmail(String email, DriveRole role) throws IOException {
        service.permissions().create(fileId, new Permission()
                .setType("user")
                .setRole(role.name().toLowerCase())
                .setEmailAddress(email)
        ).execute();
    }

    public File getFile() throws IOException {
        return service.files().get(fileId).execute();
    }
}
