@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.user.Roles
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.guild.GuildKey
@import net.dv8tion.jda.api.entities.Guild
@import net.dv8tion.jda.api.entities.User
@param WebStore ws
@param GuildDB db
@param Guild guild
@param User user
@template.main(ws = ws, title = db.getGuild().getName() + " Bank Index", content = @`
    <h2>Member Pages</h2>
    <a class="m-1 btn btn-primary btn-lg" href="${db.getGuild().getIdLong()}/deposits">View Your Deposits</a>
    @if(db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW) == Boolean.TRUE && Roles.ECON_WITHDRAW_SELF.has(user, guild))
        <a class="m-1 btn btn-primary btn-lg" href="/${db.getGuild().getIdLong()}/withdrawaa">Withdraw your deposits</a>
    @endif

    @if(Roles.ECON.has(user, db.getGuild()))
        <h2>Econ Gov Pages</h2>
        <a class="m-1 btn btn-primary btn-lg" href="/${db.getGuild().getIdLong()}/memberdeposits">View Member Deposits</a>
        <a class="m-1 btn btn-primary btn-lg" href="/${db.getGuild().getIdLong()}/withdrawaa">Send From Offshore</a>
    @endif
`, navbar = null)