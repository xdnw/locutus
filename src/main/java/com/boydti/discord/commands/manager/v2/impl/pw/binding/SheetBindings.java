package com.boydti.discord.commands.manager.v2.impl.pw.binding;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.util.sheet.templates.TransferSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;

public class SheetBindings extends BindingHelper {
    @Binding()
    public SpreadSheet sheet(String input) throws GeneralSecurityException, IOException {
        String spreadsheetId;
        if (input.startsWith("sheet:")) {
            spreadsheetId = input.split(":")[1];
        } else if (input.startsWith("https://docs.google.com/spreadsheets/d/")){
            spreadsheetId = input.split("/")[5];
        } else {
            throw new IllegalArgumentException("Invalid sheet: `" + input + "`");
        }
        return SpreadSheet.create(input);
    }

    @Binding()
    public TransferSheet transferSheet(String input) throws GeneralSecurityException, IOException {
        sheet(input); // validate
        TransferSheet sheet = new TransferSheet(input);

        Set<String> invalid = sheet.read();
        if (!invalid.isEmpty()) throw new IllegalArgumentException("Invalid nations/alliances: " + StringMan.getString(invalid));

        return sheet;
    }
}
