package link.locutus.discord.util.sheet;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.model.Revision;
import com.google.api.services.drive.model.RevisionList;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class DriveFile {
    private static final String APPLICATION_NAME = "DriveFile";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private final Drive service;
    private final String fileId;

    public DriveFile(String fileId) throws GeneralSecurityException, IOException {
        this.service = SheetUtil.getDriveService();
        this.fileId = fileId;
    }

    public void purgeVersionHistory() throws IOException {
        // List all revisions of the file
        RevisionList revisions = SheetUtil.executeRequest(SheetUtil.RequestType.DRIVE, () -> service.revisions().list(fileId).execute());
        for (Revision revision : revisions.getRevisions()) {
            // Delete each revision
            SheetUtil.executeRequest(SheetUtil.RequestType.DRIVE, () -> service.revisions().delete(fileId, revision.getId()).execute());
        }
    }

    public String getId() {
        return fileId;
    }

    public enum DriveRole {
        READER,
        WRITER,
        ORGANIZER
    }

    public static File createFile(String title, String html) throws IOException, GeneralSecurityException {
        File fileMetadata = new File();
        fileMetadata.setName(title + ".html");
        fileMetadata.setMimeType("application/vnd.google-apps.document");

        // Convert the HTML string to a file content object.
        ByteArrayContent content = ByteArrayContent.fromString("text/html", html);

        // Upload the file to Google Drive.
        File file = SheetUtil.executeRequest(SheetUtil.RequestType.DRIVE, () -> SheetUtil.getDriveService().files().create(fileMetadata, content)
                .setFields("id")
                .execute());
        return file;
    }

    public void shareWithAnyone(DriveRole role) throws IOException {
        SheetUtil.executeRequest(SheetUtil.RequestType.DRIVE, () -> service.permissions().create(fileId, new Permission()
                .setType("anyone")
                .setRole(role.name().toLowerCase())
        ).execute());
    }


    public void shareWithEmail(String email, DriveRole role) throws IOException {
        SheetUtil.executeRequest(SheetUtil.RequestType.DRIVE, () -> service.permissions().create(fileId, new Permission()
                .setType("user")
                .setRole(role.name().toLowerCase())
                .setEmailAddress(email)
        )).execute();
    }

    public File getFile() throws IOException {
        return SheetUtil.executeRequest(SheetUtil.RequestType.DRIVE, () -> service.files().get(fileId).execute());
    }
}
