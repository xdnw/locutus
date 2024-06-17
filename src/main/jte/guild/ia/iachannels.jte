@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.binding.annotation.Command
@import link.locutus.discord.commands.manager.v2.binding.annotation.Default
@import link.locutus.discord.commands.manager.v2.binding.annotation.Me
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission
@import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.Transaction2
@import link.locutus.discord.db.entities.DBAlliance
@import link.locutus.discord.db.entities.DBNation
@import link.locutus.discord.user.Roles
@import link.locutus.discord.util.StringMan
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.util.discord.DiscordUtil
@import link.locutus.discord.util.offshore.test.IACategory
@import link.locutus.discord.util.offshore.test.IAChannel
@import link.locutus.discord.util.task.ia.IACheckup
@import net.dv8tion.jda.api.entities.Guild
@import net.dv8tion.jda.api.entities.Member
@import net.dv8tion.jda.api.entities.Role
@import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
@import java.io.IOException
@import java.util.ArrayList
@import java.util.Collections
@import java.util.Comparator
@import java.util.HashMap
@import java.util.HashSet
@import java.util.List
@import java.util.Map
@import java.util.Set
@import java.util.concurrent.TimeUnit
@import java.util.stream.Collectors
@import link.locutus.discord.db.entities.InterviewMessage
@import net.dv8tion.jda.api.entities.User
@import com.google.gson.JsonElement
@param WebStore ws
@param GuildDB db
@param DBNation me
@param User author
@param IACategory iaCat
@param List<IACategory.SortedCategory> categories
@param Map<GuildMessageChannel, IACategory.SortedCategory> categoryMap
@param Map<IACategory.SortedCategory, List<GuildMessageChannel>> channelsByCategory
@param Map<GuildMessageChannel, DBNation> interviewNation
@param Map<GuildMessageChannel, User> interviewUsers
@param JsonElement avatarsJson
@param JsonElement usersJson
@param JsonElement messagesJson
@param Set<GuildMessageChannel> myChannels
@template.main(ws = ws, title = "Alliance interview channels", navbar = null, content = @`
    <div class="container">
    <div class="bg-lightmt-3 rounded shadow py-1 row">
        <div class="col-auto">
            <button type="button" cmd="${CM.interview.sortInterviews.cmd.createEmpty().toSlashCommand(false)}" refresh="1" class="btn btn-primary">Sort channels</button>
            <button type="button" cmd="${CM.interview.syncInterviews.cmd.createEmpty().toSlashCommand(false)}" refresh="1" class="btn btn-primary">List old channels</button>
        </div>
        <div class="col">
            <input class="form-control form-control-sm d-inline" id="myInput" type="text" placeholder="Search..">
        </div>
    </div>
    </div>
    </div>
    <br>
<script>
var avatars = $unsafe{avatarsJson.toString()};
var usernames = $unsafe{usersJson.toString()};
// message id, user id, date, content
var messages = $unsafe{messagesJson.toString()};
var guildId = "${db.getIdLong()}";
var avatarUrl = "${String.format(User.AVATAR_URL, "{0}", "{1}", "{2}")}";
@raw
function getAvatarUrl(userId, avatarId) {
    return avatarUrl.format(userId, avatarId, avatarId.startsWith("a_") ? "gif" : "png");
}

function msgTemplate(channelId, messageEntry) {
    var messageId = messageEntry[0];
    var userId = messageEntry[1];
    var dateLong = messageEntry[2];
    var message = messageEntry[3];
    var dateStr = new Date(parseInt(dateLong));
    var avatarId = avatars[userId];
    var avatar = getAvatarUrl(userId, avatarId);
    var username = usernames[userId];

    return `<div class="card mb-4">
        <div class="card-body">
            <div class="d-flex justify-content-between">
                <div class="d-flex flex-row align-items-center">
                    <img
                            src="${avatar}"
                            alt="avatar"
                            width="25"
                            height="25"
                    />
                    <p class="small mb-0 ms-2 me-2">${username}</p>
                    <i class="bi bi-dot"></i>
                    <p class="small text-muted mb-0 ms-2 format-date">${dateStr}</p>

                </div>
                <div class="d-flex flex-row align-items-center">
                    <p class="small text-muted mb-0"><a href="discord://discord.com/channels/${guildId}/${channelId}/${messageId}"><kbd>Jump</kbd></a></p>
                </div>
            </div>
            <p>${message}</p>
        </div>
    </div>`
}
@endraw
</script>

<div id="test">
</div>

<script>
var channelId = Object.keys(messages)[0];
    var chanMsgs = messages[channelId];
    var msg = chanMsgs[Object.keys(chanMsgs)[1]]
    document.getElementById("test").innerHTML = msgTemplate(channelId, msg);
</script>
`)