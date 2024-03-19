package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiHostingLocutus extends BotWikiGen {
    public WikiHostingLocutus(CommandManager2 manager) {
        super(manager, "hosting_locutus");
    }

    @Override
    public String generateMarkdown() {
        return """
                This guide will walk you through the process of self hosting the Locutus bot. Follow the steps below to obtain the Locutus `.jar` file, run the bot, configure the `config.yaml` file, and set up the credentials for Google Sheets.
                            
                # Table of Contents
                1. [Obtaining a Locutus `.jar` file](#obtaining-a-locutus-jar-file)
                   - [Option 1: Download a precompiled `.jar`](#option-1-download-a-precompiled-jar-option-2)
                   - [Option 2: Building from the source](#option-2-building-from-the-source)
                2. [Running the jar file](#running-the-jar-file)
                3. [Setting up the config yaml](#setting-up-the-config-yaml)
                4. [Credentials for Google Sheets](#credentials-for-google-sheets)
                            
                # 1. Obtaining a Locutus `.jar` file <a name="obtaining-a-locutus-jar-file"></a>
                            
                ### 1.1. Option 1: Download a precompiled `.jar`
                If you prefer to skip the compilation process, you can download a precompiled Locutus `.jar` file from the following location:
                - [Locutus Precompiled Jar](https://locutus.link:8443/job/locutus/) (look for the file named `shadowJar-Locutus-1.0-SNAPSHOT.jar`)
                            
                ### 1.2. Option 2: Building from the source
                If you want to build the Locutus bot from the source code, follow these steps:
                            
                #### 1.2.1. Download the source code
                1. The Locutus source code is hosted on GitHub at [xdnw/locutus](https://github.com/xdnw/locutus).
                2. On the GitHub page, you can select the branch you want to access. The main branch is called `master`, but there may be development branches with additional features.
                `Note: Development branches may not compile or run.`
                Selecting a branch:\s
                            
                ![Example 1](https://cdn.discordapp.com/attachments/1054912868706955305/1054913309620580433/image.png)
                            
                3. (option 1) Downloading the code as a zip:
                Extract the code from the zip file into the directory you want
                ![Example 1](https://cdn.discordapp.com/attachments/1054912868706955305/1054913432740188211/image.png) \s
                            
                3. (option 2) Clone the repository with git.\s
                Install git: https://git-scm.com/downloads
                Use the provided clone url from command line.
                            
                #### 1.2.2. Compiling the source code
                1. After downloading the source code, navigate to the directory that contains all the Locutus source files.
                2. Open a command prompt or terminal in that directory. (see: <https://www.lifewire.com/open-command-prompt-in-a-folder-5185505>)
                   - Example for Windows: ![Example 1](https://cdn.discordapp.com/attachments/1054912868706955305/1054946012504010762/image.png)
                   - Example for Windows: ![Example 2](https://cdn.discordapp.com/attachments/1054912868706955305/1054946335591252049/image.png)
                            
                3. Run the following command: `./gradlew build shadowJar`.
                            
                4. After the compilation finishes, you can find the compiled `.jar` file in the `locutus/build/libs` folder.
                Copy the `shadowJar-Locutus-1.0-SNAPSHOT.jar` to the directory you want to host Locutus in.\s
                (The build folder may be deleted when compiling a new jar)
                            
                ## 2. Running the jar file <a name="running-the-jar-file"></a>
                            
                To run the Locutus `.jar` file, you need to have Java installed on your system. Follow these steps:
                            
                1. Download and install Java. You will need Java 21 or a newer version. You can download Java from Oracle. I use Oracle's GraalVM for its scripting and profiling support [GraalVM Download](https://www.graalvm.org/downloads/).
                   - Note: If you encounter an `UnsupportedClassVersionError`, it means you are using an outdated Java version. Install Java 17 and ensure it is
                 set as the default version on your system. You can refer to this guide on [how to set the default Java version in Windows](https://superuser.com/a/1057552).
                2. Open a command prompt or terminal.
                3. Navigate to the directory where you downloaded the Locutus `.jar` file.
                4. Copy the jar to the directory where you want to host the bot from. `Note: Building the jar again may delete the build folder`
                5. Run the following command: `java -jar shadowJar-Locutus-1.0-SNAPSHOT.jar`.
                   - This command executes the Locutus bot. If it's your first run, the application will generate a `config/config.yaml` file and then exit. You will need to configure this file before running the bot again.
                            
                # 3. Setting up the config yaml <a name="setting-up-the-config-yaml"></a>
                            
                The Locutus bot requires a configuration file (`config.yaml`) to be set up with your P&W (Politics & War) and Discord details. Follow these steps to set up the `config.yaml` file:
                            
                1. Close the Locutus bot program if it's running.
                2. Open the `config/config.yaml` file in a text editor.
                3. Provide your P&W and Discord details in the `config.yaml` file.
                   - To register a bot with Discord, go to the [Discord Developer Portal](https://discord.com/developers/applications) and create a new application. Make sure to enable the guild and message intents for your bot user.
                   - To obtain server IDs, you can refer to this guide on [how to find your User, Server, and Message ID](https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-).
                   - To create an invite for your bot
                        - example 1: <https://cdn.discordapp.com/attachments/1054912868706955305/1054950323782950943/image.png>)
                        - example 2: <https://cdn.discordapp.com/attachments/1054912868706955305/1054950520223182938/image.png>)
                4. Save the `config.yaml` file after updating it with your details.
                5. Restart the Locutus bot by running the same command as before: `java -jar shadowJar-Locutus-1.0-SNAPSHOT.jar`.
                            
                If the Locutus bot fails to load, check the error message. You may need to configure missing values in the `config.yaml` file.
                            
                # 4. Credentials for Google Sheets <a name="credentials-for-google-sheets"></a>
                            
                If you want to enable credentials for Google Sheets, follow these steps:
                            
                1. Create credentials for Google Sheets by following the guide on the [Google Workspace Developer's Guide: Create credentials](https://developers.google.com/workspace/guides/create-credentials).
                   - During the credential creation process, select `desktop application` to authorize the credentials using a web browser.
                2. Save the credentials as `credentials-sheets.json` and `credentials-drive.json` in the `config` folder (the same folder as the `config.yaml` file).
                   - The `credentials-sheets.json` file should contain the authorization details for Google Sheets, and the `credentials-drive.json` file should contain the authorization details for Google Drive.
                   - You can refer to this Stack Overflow answer for an example of how to save your credentials: [Example on how to save your credentials](https://stackoverflow.com/a/58468671).
                   - Example format for `credentials-sheets.json`:
                     ```json
                     {
                       "installed": {
                         "client_id": "1053560905219-g86h1oodreg31gh1mfgv0mcdbejq7qmg.apps.googleusercontent.com",
                         "project_id": "quickstart-1579567009568",
                         "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                         "token_uri": "https://oauth2.googleapis.com/token",
                         "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                         "client_secret": "XXXX",
                         "redirect_uris": [
                           "urn:ietf:wg:oauth:2.0:oob",
                           "http://localhost"
                         ]
                       }
                     }
                     ```
                   - Example format for `credentials-drive.json`:
                     ```json
                     {
                       "installed": {
                         "client_id": "1053560905219-s1vbn6vdcmumih44jalu5rps01k769e9.apps.googleusercontent.com",
                         "project_id": "quickstart-1579567009568",
                         "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                         "token_uri": "https://oauth2.googleapis.com/token",
                         "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                         "client_secret": "XXXX",
                         "redirect_uris": [
                           "urn:ietf:wg:oauth:2.0:oob",
                           "http://localhost"
                         ]
                       }
                     }
                     ```
                            
                Use one of the sheet commands to test your credentials.
                            
                Congratulations! You have successfully set up the Locutus bot and configured the necessary files. The bot should now be running and ready to use with your Discord server and Google Sheets integration.
                """;
    }
}
