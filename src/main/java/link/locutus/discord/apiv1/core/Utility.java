package link.locutus.discord.apiv1.core;

public class Utility {

    public static String obfuscateApiKey(String apiKeyStr) {
        int i = apiKeyStr.lastIndexOf("key=");
        if(i==-1)
            return apiKeyStr;

        int ampersand = apiKeyStr.substring(i).indexOf('&');
        if(ampersand==-1)
            return apiKeyStr.replace(apiKeyStr.substring(i+4),"XXXX");
        return apiKeyStr.replace(apiKeyStr.substring(i+4,i+ampersand),"XXXX");
    }

    public static UnsupportedOperationException unsupported() {
        String parentMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        return new UnsupportedOperationException("The method " + parentMethodName + " is not yet supported for snapshots. Data may be available, please contact the developer.");
    }
}
