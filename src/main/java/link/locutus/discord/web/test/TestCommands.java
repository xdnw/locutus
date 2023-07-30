package link.locutus.discord.web.test;

import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.util.ImageUtil;
import link.locutus.discord.util.PnwUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }

    @Command
    public String modal(@Me IMessageIO io, ICommand command, List<String> arguments, @Default String defaults) {
        Map<String, String> args = defaults == null ? new HashMap<>() : PnwUtil.parseMap(defaults);
        io.modal().create(command, args, arguments).send();
        return null;
    }

    @Command(desc = "Get the text from a discord image\n" +
            "It is recommended to crop the image first")
    public String ocr(String discordImageUrl, @Default ImageType type) {
        if (type == null) type = ImageType.CLEAR;
        String text = ImageUtil.getText(discordImageUrl, type);
        return "```\n" +text + "\n```\n" +
        """
        OCR stands for "Optical Character Recognition," and it is a technology that converts images of text into machine-readable and editable text.
        This command does no image processing. The following is recommended:
        - Use Clear Images: Ensure that the text you want to extract is clear, level, well-lit, and not blurry.
        - Crop Unnecessary Parts: If the image contains unnecessary areas (like borders), crop the image to focus only on the text you need.
        - Use High-Quality Image Formats: Whenever possible, use high-quality formats like PNG or TIFF for better OCR results.
        - Avoid Fancy Fonts: Stick to standard, easily recognizable fonts. Avoid using decorative or handwritten-style fonts that OCR might struggle with. 
                        
        This command uses Tesseract: <https://github.com/tesseract-ocr/tesseract>
        For a GUI tool, see: <https://www.naps2.com/>
        """;

    }
}