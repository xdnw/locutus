package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public class WebMyEnemies {
    public List<Integer> alliance_ids;
    public List<String> alliances;
    public List<WebWarFinder> commands;

    public WebMyEnemies() {
        this.alliance_ids = new IntArrayList();
        this.alliances = new ObjectArrayList<>();
        this.commands = new ObjectArrayList<>();
    }
}
