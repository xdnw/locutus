package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;

public class SheetBindings extends BindingHelper {
    @Binding
    public SpreadSheet sheet(String input) throws GeneralSecurityException, IOException {
        String spreadsheetId;
        if (input.startsWith("sheet:")) {
        } else if (input.startsWith("https://docs.google.com/spreadsheets/")) {
        } else {
            throw new IllegalArgumentException("Invalid sheet: `" + input + "`");
        }
        return SpreadSheet.create(input);
    }

    @Binding
    public TransferSheet transferSheet(String input) throws GeneralSecurityException, IOException {
        sheet(input); // validate
        TransferSheet sheet = new TransferSheet(input);

        Set<String> invalid = sheet.read();
        if (!invalid.isEmpty())
            throw new IllegalArgumentException("Invalid nations/alliances: " + StringMan.getString(invalid));

        return sheet;
    }
}
