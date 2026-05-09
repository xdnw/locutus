package link.locutus.discord.treatyvis.runtime;

import java.nio.file.Path;

public final class TreatyHistoryRuntimeConfig {
    public static final int SCORE_QUANTIZATION = 100;
    public static final int TOP_N_ALLIANCES = 80;
    public static final int FLAG_ATLAS_TILE_WIDTH = 32;
    public static final int FLAG_ATLAS_TILE_HEIGHT = 24;
    public static final int FLAG_ATLAS_COLUMNS = 16;
    public static final int FLAG_DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000;
    public static final int FLAG_DOWNLOAD_READ_TIMEOUT_MS = 15_000;
    public static final int FLAG_DOWNLOAD_MAX_BYTES = 5 * 1024 * 1024;
    public static final int FLAG_IMAGE_MAX_WIDTH = 2_048;
    public static final int FLAG_IMAGE_MAX_HEIGHT = 2_048;
    public static final int FLAG_IMAGE_MAX_PIXELS = 2_048 * 2_048;
    public static final float FLAG_ICON_WEBP_QUALITY = 0.90f;
    public static final float FLAG_ATLAS_WEBP_QUALITY = 0.82f;
    public static final Path FLAG_ATLAS_CACHE_PATH = Path.of("data", "treaty_vis_runtime", "treaty_atlas.webp");
    public static final Path FLAG_ATLAS_STATE_PATH = Path.of("data", "treaty_vis_runtime", "treaty_atlas.sha256");

    private TreatyHistoryRuntimeConfig() {
    }
}