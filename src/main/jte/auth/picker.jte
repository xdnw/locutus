@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.user.Roles
@import link.locutus.discord.db.GuildDB
@import net.dv8tion.jda.api.entities.Guild
@import net.dv8tion.jda.api.entities.User
@import link.locutus.discord.db.entities.DBAlliance
@param WebStore ws
@param String discordUrl
@param String mailUrl
@template.main(ws = ws, title = "Pick a login method", content = @`
    <h3>How would you like to login?</h3>
    <ul class="nav nav-pills flex-column flex-sm-row" id="js-pills-1" role="tablist">
        <li class="nav-item border border-dark rounded-0">
            <button class="nav-link active text-start d-flex align-items-center fw-bold rounded-0" id="profile-pill" data-bs-toggle="pill" data-bs-target="#profile-pill-pane" type="button" role="tab" aria-controls="profile-pill-pane" aria-selected="true">
                <i class="bi bi-discord"></i>&nbsp;Discord OAuth
            </button>
        </li>
        <li class="nav-item border border-dark rounded-0">
            <button class="nav-link text-start d-flex align-items-center fw-bold rounded-0" id="billing-pill" data-bs-toggle="pill" data-bs-target="#billing-pill-pane" type="button" role="tab" aria-controls="billing-pill-pane" aria-selected="false">
                <i class="bi bi-envelope-fill"></i>&nbsp;Politics & War Mail
            </button>
        </li>
    </ul>
    <div class="bg-light border border-dark rounded rounded-start-0 p-3">
        <div class="tab-content" id="js-pills-content-1">
            <div class="tab-pane fade show active" id="profile-pill-pane" role="tabpanel" aria-labelledby="profile-pill" tabindex="0">
                <a class="btn fw-bold btn-sm btn-outline-success border-2 rounded" href="${discordUrl}" role="button">
                    <i class="bi bi-discord"></i> Click Here To Login
                </a>
                <hr/>
                <h5>What is discord?</h5>
                <p>Discord is a voice, video, and text chat app that's used to communicate and hang out with communities and friends.<br>
                    Discord is a can be opened in browser or installed on your computer and mobile device<br>
                    <a href="https://discord.com/download" class="btn btn-sm btn-outline-primary border-2 fw-bold">Download Discord <i class="bi bi-box-arrow-up-right"></i></a>
                </p>
            </div>
            <div class="tab-pane fade" id="billing-pill-pane" role="tabpanel" aria-labelledby="billing-pill" tabindex="0">
                <a class="btn fw-bold btn-sm btn-outline-success border-2 rounded " href="${mailUrl}" role="button">
                    <i class="bi bi-envelope-fill"></i> Click Here To Login
                </a>
                <hr/>
                <h5>Here's what you need to do:</h5>
                <ol class="list-group list-group-numbered">
                    <li class="list-group-item">Click <kbd>login</kbd> and select your nation</li>
                    <li class="list-group-item">You will be redirected to your in-game mail</li>
                    <li class="list-group-item">Click the authentication link you were sent</li>
                </ol>
            </div>
        </div>
    </div>
`, navbar = null)