package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.GoogleDoc;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;

public class SheetBindings extends BindingHelper {
    @Binding(value = "A google spreadsheet id or url", examples = {"sheet:1X2Y3Z4", "https://docs.google.com/spreadsheets/d/1X2Y3Z4/edit#gid=0"})
    public SpreadSheet sheet(String input) throws GeneralSecurityException, IOException {
        if (input.startsWith("sheet:")) {
        } else if (input.startsWith("https://docs.google.com/spreadsheets/")) {
        } else {
            throw new IllegalArgumentException("Invalid sheet: `" + input + "`");
        }
        return SpreadSheet.create(input);
    }

    @Binding(value = "A google spreadsheet id or url. Must have a `nation` or `leader` column as well as the names of each resource", examples = {"sheet:1X2Y3Z4", "https://docs.google.com/spreadsheets/d/1X2Y3Z4/edit#gid=0"})
    public TransferSheet transferSheet(String input) throws GeneralSecurityException, IOException {
        sheet(input); // validate
        TransferSheet sheet = new TransferSheet(input);

        Set<String> invalid = sheet.read();
        if (!invalid.isEmpty())
            throw new IllegalArgumentException("Invalid nations/alliances: " + StringMan.getString(invalid));

        return sheet;
    }

    @Binding(value = "A google document id or url", examples = {"document:1X2Y3Z4", "https://docs.google.com/document/d/1X2Y3Z4/edit"})
    public GoogleDoc doc(String input) throws GeneralSecurityException, IOException {
        if (input.startsWith("document:")) {
        } else if (input.startsWith("https://docs.google.com/document/")) {
        } else {
            throw new IllegalArgumentException("Invalid document: `" + input + "`");
        }
        return GoogleDoc.create(input);
    }
}
