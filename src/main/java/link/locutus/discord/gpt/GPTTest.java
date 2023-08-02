package link.locutus.discord.gpt;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AttackCursorFactory;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.VictoryCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.util.PnwUtil;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GPTTest {
    public static void main2(String[] args) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        GptHandler handler = new GptHandler();

        IEmbeddingDatabase embedding = handler.getEmbeddings();

//        List<ModerationResult> blah = handler.getModerator().moderate("self harm");
//        System.out.println(blah);

//        String response = handler.getText2text().generate("What is your name?");
//        System.out.println(response);
    }

    public static void main(String[] args) throws SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());

        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.WEB.PORT_HTTPS = 0;
        Settings.INSTANCE.WEB.PORT_HTTP = 8000;
        Settings.INSTANCE.WEB.REDIRECT = "http://localhost";
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS = 0;
        Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_DAYS = 0;
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;

        Locutus locutus = Locutus.create();
        locutus.start();

        PWGPTHandler pwGpt = locutus.getCommandManager().getV2().getPwgptHandler();
        System.out.println(pwGpt);
    }
}
