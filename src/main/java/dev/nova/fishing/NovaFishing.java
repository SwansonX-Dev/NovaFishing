package dev.nova.fishing;

import dev.nova.fishing.anticheat.AntiAutofishManager;
import dev.nova.fishing.bossbar.BossBarManager;
import dev.nova.fishing.challenge.ChallengeManager;
import dev.nova.fishing.commands.FishCommand;
import dev.nova.fishing.commands.NovaFishingCommand;
import dev.nova.fishing.commands.NovaTokenCommand;
import dev.nova.fishing.config.ConfigManager;
import dev.nova.fishing.database.DatabaseManager;
import dev.nova.fishing.discord.DiscordWebhook;
import dev.nova.fishing.event.EventManager;
import dev.nova.fishing.fishing.FishingListener;
import dev.nova.fishing.fishing.FishingManager;
import dev.nova.fishing.gui.GUIManager;
import dev.nova.fishing.hologram.HologramManager;
import dev.nova.fishing.integration.HDBHook;
import dev.nova.fishing.integration.NexoHook;
import dev.nova.fishing.integration.NovaBlockHook;
import dev.nova.fishing.integration.PAPIHook;
import dev.nova.fishing.integration.VaultHook;
import dev.nova.fishing.integration.WorldGuardHook;
import dev.nova.fishing.listeners.JoinListener;
import dev.nova.fishing.listeners.PhysicalTokenListener;
import dev.nova.fishing.listeners.RodProtectionListener;
import dev.nova.fishing.prompt.ChatPromptManager;
import dev.nova.fishing.reward.RewardManager;
import dev.nova.fishing.rod.RodManager;
import dev.nova.fishing.token.ShopManager;
import dev.nova.fishing.token.TokenManager;
import dev.nova.fishing.tournament.TournamentManager;
import dev.nova.fishing.util.TextUtil;
import dev.nova.fishing.voidfish.VoidFishingManager;
import dev.nova.fishing.wordcheck.WordCheckManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NovaFishing extends JavaPlugin {
   private static NovaFishing instance;
   public NamespacedKey rodTypeKey;
   public NamespacedKey rodLevelKey;
   public NamespacedKey rodXpKey;
   public NamespacedKey rodPrestigeKey;
   public NamespacedKey rodSkinKey;
   public NamespacedKey shopItemKey;
   public NamespacedKey tokenItemKey;
   private ConfigManager configManager;
   private DatabaseManager databaseManager;
   private RodManager rodManager;
   private RewardManager rewardManager;
   private VoidFishingManager voidFishingManager;
   private FishingManager fishingManager;
   private TokenManager tokenManager;
   private ShopManager shopManager;
   private GUIManager guiManager;
   private ChallengeManager challengeManager;
   private TournamentManager tournamentManager;
   private BossBarManager bossBarManager;
   private WordCheckManager wordCheckManager;
   private AntiAutofishManager antiAutofishManager;
   private DiscordWebhook discord;
   private ChatPromptManager promptManager;
   private EventManager eventManager;
   private HologramManager hologramManager;
   private VaultHook vaultHook;
   private PAPIHook papiHook;
   private WorldGuardHook worldGuardHook;
   private HDBHook hdbHook;
   private NovaBlockHook novaBlockHook;
   private NexoHook nexoHook;

   public void onEnable() {
      instance = this;
      this.rodTypeKey = new NamespacedKey(this, "rod_type");
      this.rodLevelKey = new NamespacedKey(this, "rod_level");
      this.rodXpKey = new NamespacedKey(this, "rod_xp");
      this.rodPrestigeKey = new NamespacedKey(this, "rod_prestige");
      this.rodSkinKey = new NamespacedKey(this, "rod_skin");
      this.shopItemKey = new NamespacedKey(this, "shop_item");
      this.tokenItemKey = new NamespacedKey(this, "token_amount");
      this.saveDefaultConfig();
      this.configManager = new ConfigManager(this);
      this.configManager.loadAll();
      this.databaseManager = new DatabaseManager(this);

      try {
         this.databaseManager.connect();
      } catch (Exception var11) {
         this.getLogger().severe("Database init failed: " + var11.getMessage());
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }

      try {
         this.vaultHook = VaultHook.attempt(this);
      } catch (Throwable var10) {
         this.getLogger().warning("Vault skipped: " + var10.getMessage());
      }

      try {
         this.papiHook = PAPIHook.attempt(this);
      } catch (Throwable var9) {
         this.getLogger().warning("PAPI skipped: " + var9.getMessage());
      }

      try {
         this.worldGuardHook = WorldGuardHook.attempt(this);
      } catch (Throwable var8) {
         this.getLogger().warning("WorldGuard skipped: " + var8.getMessage());
      }

      try {
         this.hdbHook = HDBHook.attempt(this);
      } catch (Throwable var7) {
         this.getLogger().warning("HeadDatabase skipped: " + var7.getMessage());
      }

      try {
         this.novaBlockHook = NovaBlockHook.attempt(this);
      } catch (Throwable var6) {
         this.getLogger().warning("NovaBlock skipped: " + var6.getMessage());
      }

      try {
         this.nexoHook = NexoHook.attempt(this);
      } catch (Throwable var5) {
         this.getLogger().warning("Nexo skipped: " + var5.getMessage());
      }

      this.eventManager = new EventManager(this);
      this.rodManager = new RodManager(this);
      this.rewardManager = new RewardManager(this);
      this.voidFishingManager = new VoidFishingManager(this);
      this.tokenManager = new TokenManager(this);
      this.shopManager = new ShopManager(this);
      this.guiManager = new GUIManager(this);
      this.challengeManager = new ChallengeManager(this);
      this.tournamentManager = new TournamentManager(this);
      this.bossBarManager = new BossBarManager(this);
      this.wordCheckManager = new WordCheckManager(this);
      this.antiAutofishManager = new AntiAutofishManager(this);
      this.discord = new DiscordWebhook(this);
      this.promptManager = new ChatPromptManager(this);
      this.fishingManager = new FishingManager(this);
      this.hologramManager = new HologramManager(this);
      this.hologramManager.start();
      this.getServer().getPluginManager().registerEvents(new FishingListener(this), this);
      this.getServer().getPluginManager().registerEvents(this.guiManager, this);
      this.getServer().getPluginManager().registerEvents(new JoinListener(this), this);
      this.getServer().getPluginManager().registerEvents(new RodProtectionListener(this), this);
      this.getServer().getPluginManager().registerEvents(new PhysicalTokenListener(this), this);
      this.getServer().getPluginManager().registerEvents(this.wordCheckManager, this);
      this.getServer().getPluginManager().registerEvents(this.antiAutofishManager, this);
      this.wordCheckManager.start();
      this.getServer().getPluginManager().registerEvents(this.promptManager, this);
      if (this.getConfig().getBoolean("settings.bossbar.enabled", true)) {
         this.bossBarManager.start();
      }

      NovaFishingCommand fishCmd = new NovaFishingCommand(this);
      PluginCommand nfCmd = this.getCommand("novafishing");
      if (nfCmd != null) {
         nfCmd.setExecutor(fishCmd);
         nfCmd.setTabCompleter(fishCmd);
      } else {
         this.getLogger().warning("Command 'novafishing' missing from plugin.yml");
      }

      NovaTokenCommand tokCmd = new NovaTokenCommand(this);
      PluginCommand ntCmd = this.getCommand("novatoken");
      if (ntCmd != null) {
         ntCmd.setExecutor(tokCmd);
         ntCmd.setTabCompleter(tokCmd);
      } else {
         this.getLogger().warning("Command 'novatoken' missing from plugin.yml");
      }

      FishCommand fishCastCmd = new FishCommand(this);
      PluginCommand fCmd = this.getCommand("fish");
      if (fCmd != null) {
         fCmd.setExecutor(fishCastCmd);
      } else {
         this.getLogger().warning("Command 'fish' missing from plugin.yml");
      }

      this.getLogger()
         .info(
            TextUtil.stripTags(
               "<gradient:#FF6A00:#FFD200>NovaFishing</gradient> enabled. Rods: "
                  + this.rodManager.getRodIds().size()
                  + ", Tiers: "
                  + this.rewardManager.getTierCount()
                  + ", Shop categories: "
                  + this.shopManager.getCategoryCount()
                  + ", Challenges: "
                  + this.challengeManager.getAll().size()
            )
         );
   }

   public void onDisable() {
      if (this.wordCheckManager != null) {
         this.wordCheckManager.stop();
      }

      if (this.hologramManager != null) {
         this.hologramManager.shutdown();
      }

      if (this.bossBarManager != null) {
         this.bossBarManager.stop();
      }

      if (this.tournamentManager != null && this.tournamentManager.isActive()) {
         this.tournamentManager.stop();
      }

      if (this.fishingManager != null) {
         this.fishingManager.shutdown();
      }

      if (this.databaseManager != null) {
         this.databaseManager.close();
      }

      if (this.papiHook != null) {
         this.papiHook.unregisterSafe();
      }

      instance = null;
   }

   public void reloadAll() {
      this.reloadConfig();
      this.configManager.loadAll();
      this.rodManager.reload();
      this.rewardManager.reload();
      this.voidFishingManager.reload();
      this.shopManager.reload();
      this.challengeManager.reload();
      this.tournamentManager.reload();
      if (this.wordCheckManager != null) {
         this.wordCheckManager.reschedule();
      }
   }

   public static NovaFishing get() {
      return instance;
   }

   public ConfigManager configs() {
      return this.configManager;
   }

   public DatabaseManager db() {
      return this.databaseManager;
   }

   public RodManager rods() {
      return this.rodManager;
   }

   public RewardManager rewards() {
      return this.rewardManager;
   }

   public VoidFishingManager voids() {
      return this.voidFishingManager;
   }

   public FishingManager fishing() {
      return this.fishingManager;
   }

   public TokenManager tokens() {
      return this.tokenManager;
   }

   public ShopManager shop() {
      return this.shopManager;
   }

   public GUIManager gui() {
      return this.guiManager;
   }

   public ChallengeManager challenges() {
      return this.challengeManager;
   }

   public TournamentManager tournament() {
      return this.tournamentManager;
   }

   public BossBarManager bossbar() {
      return this.bossBarManager;
   }

   public WordCheckManager wordCheck() {
      return this.wordCheckManager;
   }

   public AntiAutofishManager antiAutofish() {
      return this.antiAutofishManager;
   }

   public DiscordWebhook discord() {
      return this.discord;
   }

   public ChatPromptManager prompts() {
      return this.promptManager;
   }

   public EventManager events() {
      return this.eventManager;
   }

   public HologramManager holograms() {
      return this.hologramManager;
   }

   public VaultHook vault() {
      return this.vaultHook;
   }

   public PAPIHook papi() {
      return this.papiHook;
   }

   public WorldGuardHook worldGuard() {
      return this.worldGuardHook;
   }

   public HDBHook headDb() {
      return this.hdbHook;
   }

   public NovaBlockHook novaBlock() {
      return this.novaBlockHook;
   }

   public NexoHook nexo() {
      return this.nexoHook;
   }
}
