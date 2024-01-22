var activeFields, colNum = 1, num = 0,
toRGB = (hex, reversed, integer) => {
	if (reversed) return '#' + hex.match(/[\d]+/g).map(x => parseInt(x).toString(16).padStart(2, '0')).join('');
	if (integer) return parseInt(hex.match(/[\d]+/g).map(x => parseInt(x).toString(16).padStart(2, '0')).join(''), 16);
	if (hex.includes(',')) return hex.match(/[\d]+/g);
	hex = hex.replace('#', '').match(/.{1,2}/g)
	return [parseInt(hex[0], 16), parseInt(hex[1], 16), parseInt(hex[2], 16), 1];
};

function markup(txt, opts) {
    txt = txt
        .replace(/&#60;(https?:\/\/[^"<>]+?)&#62;/g, `<a href="$1">$1</a>`)
        .replace(/\&#60;:[^:"<>]+:(\d+)\&#62;/g, '<img class="emoji" src="https://cdn.discordapp.com/emojis/$1.png"/>')
        .replace(/\&#60;a:[^:"<>]+:(\d+)\&#62;/g, '<img class="emoji" src="https://cdn.discordapp.com/emojis/$1.gif"/>')
        .replace(/~~(.+?)~~/g, '<s>$1</s>')
        .replace(/\*\*\*(.+?)\*\*\*/g, '<em><strong>$1</strong></em>')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/__(.+?)__/g, '<u>$1</u>')
        .replace(/\*(.+?)\*/g, '<em>$1</em>')
        .replace(/_(.+?)_/g, '<em>$1</em>')
    txt = txt.replace(/\`([^\`]+?)\`|\`\`([^\`]+?)\`\`|\`\`\`((?:\n|.)+?)\`\`\`/g, (m, x, y, z) => x ? `<code class="inline">${x}</code>` : y ? `<code class="inline">${y}</code>` : z ? `<code class="inline">${z}</code>` : m);
    txt = txt.replace(/\`\`\`(\w{1,15})?\n((?:\n|.)+?)\`\`\`|\`\`(.+?)\`\`(?!\`)|\`([^\`]+?)\`/g, (m, w, x, y, z) => w && x ? `<pre><code class="${w}">${x}</code></pre>` : x ? `<pre><code class="hljs nohighlight">${x}</code></pre>` : y || z ? `<code class="inline">${y || z}</code>` : m);
    txt = txt.replace(/\[([^\[\]]+)\]\((.+?)\)/g, `<a title="$1" target="_blank" class="anchor" href="$2">$1</a>`);
    if (opts.replaceEmojis) txt = txt.replace(/(?<!code(?: \w+=".+")?>[^>]+)(?<!\/[^\s"]+?):((?!\/)\w+):/g, (match, x) => x && emojis[x] ? emojis[x] : match);
    txt = txt
        .replace(/(?<=\n|^)\s*&#62;\s+([^\n]+)/g, '<div class="blockquote"><div class="blockquoteDivider"></div><blockquote>$1</blockquote></div>')
        .replace(/\n/g, '<br>');
    return txt;
}

function createEmbed(elemRoot, json, author, avatar, outDiv) {
//	    <div class="contents">
//            <img src="${avatar}" class="avatar" alt=" " />
//            <h2>
//                <span class="username" role="button">${author}</span><span class="botTag"><span class="botText" style="top: 1.12px;">BOT</span></span>
//                <span class="timeText"></span>
//            </h2>
//        </div>

	elemRoot.innerHTML = `
<div class="msgEmbed">
    <div class="markup messageContent"></div>
    <div class="container">
    </div>
    <div class="emptyTxt"></div>
</div>
<div class="bottomSide">
    <div class="notification">There is an error</div>
</div>`;

    let msgEmbed = elemRoot.querySelector('.msgEmbed')
	let url = (url) => /^(https?:)?\/\//g.exec(url) ? url : '//' + url;
	let notif = elemRoot.querySelector('.notification');
	let makeShort = (txt, length, mediaWidth) => {
		if (mediaWidth && window.matchMedia(`(max-width:${mediaWidth}px)`).matches)
			return txt.length > (length - 3) ? txt.substring(0, length - 3) + '...' : txt;
		return txt;
	};
	let error = (msg, time) => {
		notif.innerHTML = msg, notif.style.display = 'block';
		time && setTimeout(() => notif.animate({ opacity: '0', bottom: '-50px', offset: 1 }, { easing: 'ease', duration: 500 })
			.onfinish = () => notif.style.removeProperty('display'), time);
		return false;
	};
	let allGood = e => {
		let str = JSON.stringify(e, null, 4), re = /("(?:icon_)?url": *")((?!\w+?:\/\/).+)"/g.exec(str);
		if (e.timestamp && new Date(e.timestamp).toString() === "Invalid Date") return error('Timestamp is invalid');
		if (re) { // If a URL is found without a protocol
			if (!/\w+:|\/\/|^\//g.exec(re[2]) && re[2].includes('.')) {
				return true;
			}
			return error(`URL should have a protocol. Did you mean <span class="inline full short">http://${makeShort(re[2], 30, 600).replace(' ', '')}</span>?`);
		}
		return true;
	};
	let encodeHTML = str => str.replace(/[\u00A0-\u9999<>\&]/g, i => '&#' + i.charCodeAt(0) + ';');
	let tstamp = stringISO => {
		let date = stringISO ? new Date(stringISO) : new Date(),
			dateArray = date.toLocaleString('en-US', { hour: 'numeric', hour12: true, minute: 'numeric' }),
			today = new Date(),
			yesterday = new Date(new Date().setDate(today.getDate() - 1));
		return today.toDateString() === date.toDateString() ? `Today at ${dateArray}` :
			yesterday.toDateString() === date.toDateString() ? `Yesterday at ${dateArray}` :
				`${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}/${date.getFullYear()}`;
	};
	let display = (el, data, displayType) => {
		if (data) el.innerHTML = data;
		el.style.display = displayType || "unset";
	};
	let hide = el => el.style.removeProperty('display');
	let imgSrc = (elm, src, remove) => remove ? elm.style.removeProperty('content') : elm.style.content = `url(${src})`;
		
	try {
	    let embedContent = elemRoot.querySelector('.messageContent');
        let embedCont = elemRoot.querySelector('.messageContent + .container');
		if (!json.content) embedContent.classList.add('empty');
		else {
			embedContent.innerHTML = markup(encodeHTML(json.content), { replaceEmojis: true });
			embedContent.classList.remove('empty');
		}
		let embeds = [];
		if (json.embed && Object.keys(json.embed).length) {
		    console.log("ADD EMBED (1)");
		    embeds.push(json.embed);
		}
		if (json.embeds && json.embeds.length > 0) {
		    for (const embed of json.embeds) {
		        console.log("ADD EMBEDS (2)");
		        embeds.push(embed);
		    }
		}
		if (embeds.length == 0) {
		    embedCont.classList.add('empty')
		}
		console.log("NUM EMBEDS: " + embeds.length);
		for (const e of embeds) {
            if (!allGood(e)) return;

            console.log("JSON: " + JSON.stringify(e));

            let embed = document.createElement('div');
            embed.innerHTML = `
<div class="embedGrid">
<div class="embedAuthor embedMargin"></div>
<div class="embedTitle embedMargin"></div>
<div class="embedDescription embedMargin"></div>
<div class="embedFields"></div>
<img class="imageWrapper clickable embedMedia embedImage" onerror="this.style.display='none'"/>
<img class="imageWrapper clickable embedThumbnail" onerror="this.style.display='none'"/>
<div class="embedFooter embedMargin"></div>
</div>`
            embedCont.appendChild(embed);
            embed.classList.add("embed", "markup");
            let embedTitle = embed.querySelector('.embedTitle');
            let embedDescription = embed.querySelector('.embedDescription');
            let embedAuthor = embed.querySelector('.embedAuthor');
            let embedFooter = embed.querySelector('.embedFooter');
            let embedImage = embed.querySelector('.embedImage');
            let embedThumbnail = embed.querySelector('.embedThumbnail');
            let embedFields = embed.querySelector('.embedFields');

            if (e.title) display(embedTitle, markup(`${e.url ? '<a class="anchor" target="_blank" href="' + encodeHTML(url(e.url)) + '">' + encodeHTML(e.title) + '</a>' : encodeHTML(e.title)}`, { replaceEmojis: true, inlineBlock: true }));
            else hide(embedTitle);
            if (e.description) display(embedDescription, markup(encodeHTML(e.description), { inEmbed: true, replaceEmojis: true }));
            else hide(embedDescription);
            if (e.color) embed.closest('.embed').style.borderColor = encodeHTML(typeof e.color === 'number' ? '#' + e.color.toString(16).padStart(6, "0") : e.color);
            else embed.closest('.embed').style.removeProperty('border-color');
            if (e.author && e.author.name) display(embedAuthor, `
                ${e.author.icon_url ? '<img class="embedAuthorIcon" src="' + encodeHTML(url(e.author.icon_url)) + '">' : ''}
                ${e.author.url ? '<a class="embedAuthorNameLink embedLink embedAuthorName" href="' + encodeHTML(url(e.author.url)) + '" target="_blank">' + encodeHTML(e.author.name) + '</a>' : '<span class="embedAuthorName">' + encodeHTML(e.author.name) + '</span>'}`, 'flex');
            else hide(embedAuthor);
            if (e.thumbnail && e.thumbnail.url) embedThumbnail.src = encodeHTML(e.thumbnail.url), embedThumbnail.style.display = 'block';
            else hide(embedThumbnail);
            if (e.image && e.image.url) embedImage.src = encodeHTML(e.image.url), embedImage.style.display = 'block';
            else hide(embedImage);
            if (e.footer && e.footer.text) display(embedFooter, `
                ${e.footer.icon_url ? '<img class="embedFooterIcon" src="' + encodeHTML(url(e.footer.icon_url)) + '">' : ''}<span class="embedFooterText">
                    ${encodeHTML(e.footer.text)}
                ${e.timestamp ? '<span class="embedFooterSeparator">•</span>' + encodeHTML(tstamp(e.timestamp)) : ''}</span></div>`, 'flex');
            else if (e.timestamp) display(embedFooter, `<span class="embedFooterText">${encodeHTML(tstamp(e.timestamp))}</span></div>`, 'flex');
            else hide(embedFooter);
            if (e.fields) {
                embedFields.innerHTML = '';
                e.fields.forEach(f => {
                    if (f.name && f.value) {
                        if (!f.inline) {
                            let el = embedFields.insertBefore(document.createElement('div'), null);
                            el.outerHTML = `
                        <div class="embedField" style="grid-column: 1 / 13;">
                            <div class="embedFieldName">${markup(encodeHTML(f.name), { inEmbed: true, replaceEmojis: true, inlineBlock: true })}</div>
                            <div class="embedFieldValue">${markup(encodeHTML(f.value), { inEmbed: true, replaceEmojis: true })}</div>
                        </div>`;
                        } else {
                            el = embedFields.insertBefore(document.createElement('div'), null);
                            el.outerHTML = `
                        <div class="embedField ${num}" style="grid-column: ${colNum} / ${colNum + 4};">
                            <div class="embedFieldName">${markup(encodeHTML(f.name), { inEmbed: true, replaceEmojis: true, inlineBlock: true })}</div>
                            <div class="embedFieldValue">${markup(encodeHTML(f.value), { inEmbed: true, replaceEmojis: true })}</div>
                        </div>`;
                            colNum = (colNum === 9 ? 1 : colNum + 4);
                            num++;
                        }
                    }
                });
                colNum = 1;
                let len = e.fields.filter(f => f.inline).length;
                if (len === 2 || (len > 3 && len % 2 !== 0)) {
                    let children = embedFields.children;
                    children[0] && (children[0].style.gridColumn = '1 / 7');
                    children[1] && (children[1].style.gridColumn = '7 / 13');
                    embed.style.maxWidth = "408px";
                } else
                    embed.style.removeProperty('max-width');
                display(embedFields, undefined, 'grid');
            } else hide(embedFields);
            embedCont.classList.remove('empty');
        }

        if (json.reactions && json.reactions.length > 0) {
            var reactionRow = document.createElement("div");
            reactionRow.classList.add("row");
            msgEmbed.append(reactionRow);
            for (const emoji of json.reactions) {
                var btn = document.createElement("button");
                btn.className = "btn btn-primary btn-sm";
                btn.innerHTML = emoji;
                btn.onclick = function(){onMessageReact(emoji, json.id, json, outDiv, btn)};
                reactionRow.append(btn);
            }
        }

        elemRoot.querySelectorAll('.markup pre > code').forEach((block) => hljs.highlightBlock(block));
        notif.animate({ opacity: '0', bottom: '-50px', offset: 1 }, { easing: 'ease', duration: 500 }).onfinish = () => notif.style.removeProperty('display');
        twemoji.parse(msgEmbed);
	} catch (e) {
		console.log("Error", e.stack);
		console.log("Error", e.name);
		console.log("Error", e.message);
	}

};

var json = json = {
    content: "You can~~not~~ do `this`.```py\nAnd this.\nprint('Hi')```\n*italics* or _italics_     __*underline italics*__\n**bold**     __**underline bold**__\n***bold italics***  __***underline bold italics***__\n__underline__     ~~Strikethrough~~",
    embeds: [{"description":"body","title":"title"},{
         title: "Hello ~~people~~ world :wave:",
         description: "You can use [links](https://discord.com) or emojis :smile: 😎\n```\nAnd also code blocks\n```",
         color: 4321431,
         timestamp: new Date().toISOString(),
         url: "https://discord.com",
         author: {
             name: "Author name",
             url: "https://discord.com",
             icon_url: "https://unsplash.it/100"
         },
         thumbnail: {
             url: "https://unsplash.it/200"
         },
         image: {
             url: "https://unsplash.it/380/200"
         },
         footer: {
             text: "Footer text",
             icon_url: "https://unsplash.it/100"
         },
         fields: [
             {
                 name: "Field 1, *lorem* **ipsum**, ~~dolor~~",
                 value: "Field value"
             },
             {
                 name: "Field 2",
                 value: "You can use custom emojis <:Kekwlaugh:722088222766923847>. <:GangstaBlob:742256196295065661>",
                 inline: false
             },
             {
                 name: "Inline field",
                 value: "Fields can be inline",
                 inline: true
             },
             {
                 name: "Inline field",
                 value: "*Lorem ipsum*",
                 inline: true
             },
             {
                 name: "Inline field",
                 value: "value",
                 inline: true
             },
             {
                 name: "Another field",
                 value: "> Nope, didn't forget about this",
                 inline: false
             }
         ]
     }]
};

function loadMarkup() {
    var elems = document.getElementsByClassName("markup");
    for (var elem of elems) {
        elem.innerHTML = markup(elem.innerHTML, {replaceEmojis: true });
    }
}

$( document ).ready(function() {
    loadMarkup();
});

//window.onload = () => {
//    const AVATAR = "https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp?size=256";
//    const AUTHOR_NAME = "Locutus";
//	root = document.getElementById("root");
//	createEmbed(root, json, AUTHOR_NAME, AVATAR);
//
//}