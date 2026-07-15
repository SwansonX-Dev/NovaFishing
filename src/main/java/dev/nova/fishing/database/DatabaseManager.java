package dev.nova.fishing.database;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.reward.RewardTier;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

public final class DatabaseManager {
   public static final int CURRENT_SCHEMA = 4;
   private final NovaFishing plugin;
   private Connection connection;
   private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "NovaFishing-DB");
      t.setDaemon(true);
      return t;
   });

   public DatabaseManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void runAsync(Runnable task) {
      if (this.writeExecutor.isShutdown()) {
         task.run();
      } else {
         this.writeExecutor.submit(() -> {
            try {
               task.run();
            } catch (Throwable var3) {
               this.plugin.getLogger().warning("Async DB task: " + var3.getMessage());
            }
         });
      }
   }

   public synchronized void connect() throws SQLException {
      File folder = this.plugin.getDataFolder();
      if (!folder.exists()) {
         folder.mkdirs();
      }

      String fileName = this.plugin.getConfig().getString("database.file", "data.db");
      File dbFile = new File(folder, fileName);

      try {
         Class.forName("org.sqlite.JDBC");
      } catch (ClassNotFoundException var9) {
         throw new SQLException("SQLite driver not found", var9);
      }

      this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

      try (Statement s = this.connection.createStatement()) {
         s.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (\n  version INTEGER NOT NULL\n);");
         s.executeUpdate(
            "CREATE TABLE IF NOT EXISTS player_data (\n  uuid TEXT PRIMARY KEY,\n  name TEXT,\n  tokens BIGINT NOT NULL DEFAULT 0,\n  total_xp BIGINT NOT NULL DEFAULT 0,\n  total_catches BIGINT NOT NULL DEFAULT 0,\n  highest_max_rod TEXT,\n  current_rod_level INTEGER NOT NULL DEFAULT 0,\n  last_seen BIGINT NOT NULL DEFAULT 0,\n  notify_token INTEGER NOT NULL DEFAULT 1,\n  fishing_tokens_earned BIGINT NOT NULL DEFAULT 0\n);"
         );
         s.executeUpdate(
            "CREATE TABLE IF NOT EXISTS broadcast_history (\n  uuid TEXT NOT NULL,\n  rod_id TEXT NOT NULL,\n  broadcast_at BIGINT NOT NULL,\n  PRIMARY KEY(uuid, rod_id)\n);"
         );
         s.executeUpdate(
            "CREATE TABLE IF NOT EXISTS tier_catches (\n  uuid TEXT NOT NULL,\n  tier TEXT NOT NULL,\n  count BIGINT NOT NULL DEFAULT 0,\n  PRIMARY KEY(uuid, tier)\n);"
         );
         s.executeUpdate(
            "CREATE TABLE IF NOT EXISTS prestige_records (\n  uuid TEXT NOT NULL,\n  rod_id TEXT NOT NULL,\n  prestige INTEGER NOT NULL DEFAULT 0,\n  PRIMARY KEY(uuid, rod_id)\n);"
         );
         s.executeUpdate(
            "CREATE TABLE IF NOT EXISTS challenge_progress (\n  uuid TEXT NOT NULL,\n  challenge_id TEXT NOT NULL,\n  progress BIGINT NOT NULL DEFAULT 0,\n  completed INTEGER NOT NULL DEFAULT 0,\n  last_reset BIGINT NOT NULL DEFAULT 0,\n  PRIMARY KEY(uuid, challenge_id)\n);"
         );
         s.executeUpdate(
            "CREATE TABLE IF NOT EXISTS token_earnings (\n  uuid TEXT NOT NULL,\n  amount BIGINT NOT NULL,\n  ts BIGINT NOT NULL\n);"
         );
         s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_token_earnings_ts ON token_earnings(ts);");
         s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_token_earnings_uuid_ts ON token_earnings(uuid, ts);");
      }

      int existing = this.readSchemaVersion();
      if (existing < CURRENT_SCHEMA) {
         this.backup();
         this.migrate(existing, CURRENT_SCHEMA);
         this.writeSchemaVersion(CURRENT_SCHEMA);
         this.plugin.getLogger().info("Database migrated from schema " + existing + " to " + CURRENT_SCHEMA);
      } else if (existing == 0) {
         this.writeSchemaVersion(CURRENT_SCHEMA);
      }
   }

   private int readSchemaVersion() {
      try {
         int var3;
         try (
            Statement s = this.connection.createStatement();
            ResultSet rs = s.executeQuery("SELECT version FROM schema_version LIMIT 1");
         ) {
            var3 = rs.next() ? rs.getInt(1) : 0;
         }

         return var3;
      } catch (SQLException var9) {
         return 0;
      }
   }

   private void writeSchemaVersion(int v) {
      try (Statement s = this.connection.createStatement()) {
         s.executeUpdate("DELETE FROM schema_version");
         s.executeUpdate("INSERT INTO schema_version(version) VALUES(" + v + ")");
      } catch (SQLException var7) {
         this.plugin.getLogger().warning("writeSchemaVersion: " + var7.getMessage());
      }
   }

   private void backup() {
      try {
         String fileName = this.plugin.getConfig().getString("database.file", "data.db");
         File src = new File(this.plugin.getDataFolder(), fileName);
         if (!src.exists()) {
            return;
         }

         File bak = new File(this.plugin.getDataFolder(), fileName + ".backup-" + System.currentTimeMillis());
         Files.copy(src.toPath(), bak.toPath());
         this.plugin.getLogger().info("Pre-migration backup written to " + bak.getName());
      } catch (Exception var4) {
         this.plugin.getLogger().warning("Backup failed (continuing anyway): " + var4.getMessage());
      }
   }

   private void migrate(int fromVersion, int toVersion) {
      if (fromVersion < 2) {
         try (Statement s = this.connection.createStatement()) {
            s.executeUpdate("ALTER TABLE player_data ADD COLUMN notify_token INTEGER NOT NULL DEFAULT 1");
         } catch (SQLException var11) {
            if (!var11.getMessage().toLowerCase().contains("duplicate column")) {
               this.plugin.getLogger().warning("migrate v2 (notify_token): " + var11.getMessage());
            }
         }
      }

      if (fromVersion < 3) {
         try (Statement sx = this.connection.createStatement()) {
            sx.executeUpdate("ALTER TABLE player_data ADD COLUMN current_rod_level INTEGER NOT NULL DEFAULT 0");
         } catch (SQLException var9) {
            if (!var9.getMessage().toLowerCase().contains("duplicate column")) {
               this.plugin.getLogger().warning("migrate v3 (current_rod_level): " + var9.getMessage());
            }
         }
      }

      if (fromVersion < 4) {
         try (Statement sx = this.connection.createStatement()) {
            sx.executeUpdate("ALTER TABLE player_data ADD COLUMN fishing_tokens_earned BIGINT NOT NULL DEFAULT 0");
         } catch (SQLException var9) {
            if (!var9.getMessage().toLowerCase().contains("duplicate column")) {
               this.plugin.getLogger().warning("migrate v4 (fishing_tokens_earned): " + var9.getMessage());
            }
         }

         try (Statement sx = this.connection.createStatement()) {
            sx.executeUpdate(
               "CREATE TABLE IF NOT EXISTS token_earnings (\n  uuid TEXT NOT NULL,\n  amount BIGINT NOT NULL,\n  ts BIGINT NOT NULL\n);"
            );
            sx.executeUpdate("CREATE INDEX IF NOT EXISTS idx_token_earnings_ts ON token_earnings(ts);");
            sx.executeUpdate("CREATE INDEX IF NOT EXISTS idx_token_earnings_uuid_ts ON token_earnings(uuid, ts);");
         } catch (SQLException var9) {
            this.plugin.getLogger().warning("migrate v4 (token_earnings): " + var9.getMessage());
         }
      }
   }

   public synchronized void close() {
      this.writeExecutor.shutdown();

      try {
         if (!this.writeExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.plugin.getLogger().warning("DB write queue did not drain in 5s; forcing shutdown.");
            this.writeExecutor.shutdownNow();
         }
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         this.writeExecutor.shutdownNow();
      }

      if (this.connection != null) {
         try {
            this.connection.close();
         } catch (SQLException var2) {
         }

         this.connection = null;
      }
   }

   public synchronized Connection raw() {
      return this.connection;
   }

   public synchronized void touchPlayer(UUID uuid, String name) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO player_data(uuid, name, last_seen) VALUES(?,?,?)\nON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen;"
            )) {
         p.setString(1, uuid.toString());
         p.setString(2, name);
         p.setLong(3, System.currentTimeMillis());
         p.executeUpdate();
      } catch (SQLException var8) {
         this.plugin.getLogger().warning("touchPlayer: " + var8.getMessage());
      }
   }

   public synchronized long getTokens(UUID uuid) {
      try {
         long var4;
         try (PreparedStatement p = this.connection.prepareStatement("SELECT tokens FROM player_data WHERE uuid=?")) {
            p.setString(1, uuid.toString());

            try (ResultSet rs = p.executeQuery()) {
               if (!rs.next()) {
                  return 0L;
               }

               var4 = rs.getLong(1);
            }
         }

         return var4;
      } catch (SQLException var10) {
         this.plugin.getLogger().warning("getTokens: " + var10.getMessage());
         return 0L;
      }
   }

   public synchronized void setTokens(UUID uuid, long value) {
      try (PreparedStatement p = this.connection
            .prepareStatement("INSERT INTO player_data(uuid, tokens) VALUES(?,?)\nON CONFLICT(uuid) DO UPDATE SET tokens=excluded.tokens;")) {
         p.setString(1, uuid.toString());
         p.setLong(2, Math.max(0L, value));
         p.executeUpdate();
      } catch (SQLException var9) {
         this.plugin.getLogger().warning("setTokens: " + var9.getMessage());
      }
   }

   public synchronized void addStats(UUID uuid, long xpDelta, long catchDelta) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO player_data(uuid, total_xp, total_catches) VALUES(?,?,?)\nON CONFLICT(uuid) DO UPDATE SET\n  total_xp = total_xp + excluded.total_xp,\n  total_catches = total_catches + excluded.total_catches;"
            )) {
         p.setString(1, uuid.toString());
         p.setLong(2, xpDelta);
         p.setLong(3, catchDelta);
         p.executeUpdate();
      } catch (SQLException var11) {
         this.plugin.getLogger().warning("addStats: " + var11.getMessage());
      }
   }

   public synchronized void recordTierCatch(UUID uuid, RewardTier tier) {
      try (PreparedStatement p = this.connection
            .prepareStatement("INSERT INTO tier_catches(uuid, tier, count) VALUES(?,?,1)\nON CONFLICT(uuid, tier) DO UPDATE SET count = count + 1;")) {
         p.setString(1, uuid.toString());
         p.setString(2, tier.name());
         p.executeUpdate();
      } catch (SQLException var8) {
         this.plugin.getLogger().warning("recordTierCatch: " + var8.getMessage());
      }
   }

   public synchronized EnumMap<RewardTier, Long> getTierCatches(UUID uuid) {
      EnumMap<RewardTier, Long> out = new EnumMap<>(RewardTier.class);

      try (PreparedStatement p = this.connection.prepareStatement("SELECT tier, count FROM tier_catches WHERE uuid=?")) {
         p.setString(1, uuid.toString());

         try (ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
               RewardTier t = RewardTier.safe(rs.getString(1));
               if (t != null) {
                  out.put(t, rs.getLong(2));
               }
            }
         }
      } catch (SQLException var11) {
         this.plugin.getLogger().warning("getTierCatches: " + var11.getMessage());
      }

      return out;
   }

   public synchronized void setHighestMaxRod(UUID uuid, String rodId) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO player_data(uuid, highest_max_rod) VALUES(?,?)\nON CONFLICT(uuid) DO UPDATE SET highest_max_rod=excluded.highest_max_rod;"
            )) {
         p.setString(1, uuid.toString());
         p.setString(2, rodId);
         p.executeUpdate();
      } catch (SQLException var8) {
         this.plugin.getLogger().warning("setHighestMaxRod: " + var8.getMessage());
      }
   }

   public synchronized int getCurrentRodLevel(UUID uuid) {
      try {
         int var4;
         try (PreparedStatement p = this.connection.prepareStatement("SELECT current_rod_level FROM player_data WHERE uuid=?")) {
            p.setString(1, uuid.toString());

            try (ResultSet rs = p.executeQuery()) {
               if (!rs.next()) {
                  return 0;
               }

               var4 = rs.getInt(1);
            }
         }

         return var4;
      } catch (SQLException var10) {
         this.plugin.getLogger().warning("getCurrentRodLevel: " + var10.getMessage());
         return 0;
      }
   }

   public synchronized void setCurrentRodLevel(UUID uuid, int level) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO player_data(uuid, current_rod_level) VALUES(?,?)\nON CONFLICT(uuid) DO UPDATE SET current_rod_level=excluded.current_rod_level;"
            )) {
         p.setString(1, uuid.toString());
         p.setInt(2, Math.max(0, level));
         p.executeUpdate();
      } catch (SQLException var8) {
         this.plugin.getLogger().warning("setCurrentRodLevel: " + var8.getMessage());
      }
   }

   public synchronized String getHighestMaxRod(UUID uuid) {
      try {
         String var4;
         try (PreparedStatement p = this.connection.prepareStatement("SELECT highest_max_rod FROM player_data WHERE uuid=?")) {
            p.setString(1, uuid.toString());

            try (ResultSet rs = p.executeQuery()) {
               if (!rs.next()) {
                  return null;
               }

               var4 = rs.getString(1);
            }
         }

         return var4;
      } catch (SQLException var10) {
         this.plugin.getLogger().warning("getHighestMaxRod: " + var10.getMessage());
         return null;
      }
   }

   public synchronized boolean recordBroadcast(UUID uuid, String rodId) {
      try (PreparedStatement check = this.connection.prepareStatement("SELECT 1 FROM broadcast_history WHERE uuid=? AND rod_id=?")) {
         check.setString(1, uuid.toString());
         check.setString(2, rodId);

         try (ResultSet rs = check.executeQuery()) {
            if (rs.next()) {
               return false;
            }
         }
      } catch (SQLException var14) {
         this.plugin.getLogger().warning("recordBroadcast(check): " + var14.getMessage());
      }

      try {
         boolean var16;
         try (PreparedStatement ins = this.connection.prepareStatement("INSERT INTO broadcast_history(uuid, rod_id, broadcast_at) VALUES(?,?,?)")) {
            ins.setString(1, uuid.toString());
            ins.setString(2, rodId);
            ins.setLong(3, System.currentTimeMillis());
            ins.executeUpdate();
            var16 = true;
         }

         return var16;
      } catch (SQLException var11) {
         this.plugin.getLogger().warning("recordBroadcast(ins): " + var11.getMessage());
         return false;
      }
   }

   public synchronized void bumpPrestige(UUID uuid, String rodId, int prestige) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO prestige_records(uuid, rod_id, prestige) VALUES(?,?,?)\nON CONFLICT(uuid, rod_id) DO UPDATE SET prestige=excluded.prestige\nWHERE excluded.prestige > prestige_records.prestige;"
            )) {
         p.setString(1, uuid.toString());
         p.setString(2, rodId);
         p.setInt(3, prestige);
         p.executeUpdate();
      } catch (SQLException var9) {
         this.plugin.getLogger().warning("bumpPrestige: " + var9.getMessage());
      }
   }

   public synchronized Map<String, Integer> getPrestigeRecords(UUID uuid) {
      Map<String, Integer> out = new HashMap<>();

      try (PreparedStatement p = this.connection.prepareStatement("SELECT rod_id, prestige FROM prestige_records WHERE uuid=?")) {
         p.setString(1, uuid.toString());

         try (ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
               out.put(rs.getString(1), rs.getInt(2));
            }
         }
      } catch (SQLException var11) {
         this.plugin.getLogger().warning("getPrestigeRecords: " + var11.getMessage());
      }

      return out;
   }

   public synchronized List<DatabaseManager.TopEntry> topByXp(int limit) {
      List<DatabaseManager.TopEntry> out = new ArrayList<>();

      try (PreparedStatement p = this.connection.prepareStatement("SELECT name, total_xp, total_catches FROM player_data ORDER BY total_xp DESC LIMIT ?")) {
         p.setInt(1, Math.max(1, limit));

         try (ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
               out.add(new DatabaseManager.TopEntry(rs.getString(1), rs.getLong(2), rs.getLong(3)));
            }
         }
      } catch (SQLException var11) {
         this.plugin.getLogger().warning("topByXp: " + var11.getMessage());
      }

      return out;
   }

   public synchronized List<DatabaseManager.TopTokensEntry> topByTokens(int limit, Set<String> excludedLowerNames) {
      List<DatabaseManager.TopTokensEntry> out = new ArrayList<>();
      StringBuilder sql = new StringBuilder("SELECT name, tokens FROM player_data WHERE name IS NOT NULL");
      appendNameExclusion(sql, excludedLowerNames);
      sql.append(" ORDER BY tokens DESC LIMIT ?");

      try (PreparedStatement p = this.connection.prepareStatement(sql.toString())) {
         int idx = bindExcludedNames(p, 1, excludedLowerNames);
         p.setInt(idx, Math.max(1, limit));

         try (ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
               out.add(new DatabaseManager.TopTokensEntry(rs.getString(1), rs.getLong(2)));
            }
         }
      } catch (SQLException var14) {
         this.plugin.getLogger().warning("topByTokens: " + var14.getMessage());
      }

      return out;
   }

   public synchronized void addFishingTokensEarned(UUID uuid, long delta) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO player_data(uuid, fishing_tokens_earned) VALUES(?,?)\nON CONFLICT(uuid) DO UPDATE SET fishing_tokens_earned = fishing_tokens_earned + excluded.fishing_tokens_earned;"
            )) {
         p.setString(1, uuid.toString());
         p.setLong(2, Math.max(0L, delta));
         p.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("addFishingTokensEarned: " + ex.getMessage());
      }
   }

   public synchronized long getFishingTokensEarned(UUID uuid) {
      try (PreparedStatement p = this.connection.prepareStatement("SELECT fishing_tokens_earned FROM player_data WHERE uuid=?")) {
         p.setString(1, uuid.toString());
         try (ResultSet rs = p.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("getFishingTokensEarned: " + ex.getMessage());
         return 0L;
      }
   }

   public synchronized void logTokenEarning(UUID uuid, long amount, long ts) {
      try (PreparedStatement p = this.connection.prepareStatement("INSERT INTO token_earnings(uuid, amount, ts) VALUES(?,?,?)")) {
         p.setString(1, uuid.toString());
         p.setLong(2, amount);
         p.setLong(3, ts);
         p.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("logTokenEarning: " + ex.getMessage());
      }
   }

   public synchronized List<DatabaseManager.TopEarningsEntry> topByEarningsSince(long sinceTs, int limit, Set<String> excludedLowerNames) {
      List<DatabaseManager.TopEarningsEntry> out = new ArrayList<>();
      StringBuilder sql = new StringBuilder(
         "SELECT pd.name, SUM(te.amount) AS earned FROM token_earnings te JOIN player_data pd ON pd.uuid = te.uuid WHERE te.ts >= ? AND pd.name IS NOT NULL"
      );
      appendNameExclusion(sql, excludedLowerNames);
      sql.append(" GROUP BY te.uuid ORDER BY earned DESC LIMIT ?");

      try (PreparedStatement p = this.connection.prepareStatement(sql.toString())) {
         p.setLong(1, sinceTs);
         int idx = bindExcludedNames(p, 2, excludedLowerNames);
         p.setInt(idx, Math.max(1, limit));

         try (ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
               out.add(new DatabaseManager.TopEarningsEntry(rs.getString(1), rs.getLong(2)));
            }
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("topByEarningsSince: " + ex.getMessage());
      }

      return out;
   }

   public synchronized long getEarningsSince(UUID uuid, long sinceTs) {
      try (PreparedStatement p = this.connection.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM token_earnings WHERE uuid=? AND ts >= ?")) {
         p.setString(1, uuid.toString());
         p.setLong(2, sinceTs);
         try (ResultSet rs = p.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("getEarningsSince: " + ex.getMessage());
         return 0L;
      }
   }

   public synchronized List<DatabaseManager.TopRodEntry> topByMaxRod(int limit, ToIntFunction<String> tierIndex, Set<String> excludedLowerNames) {
      List<DatabaseManager.TopRodEntry> all = new ArrayList<>();
      StringBuilder sql = new StringBuilder(
         "SELECT name, highest_max_rod, current_rod_level, total_xp FROM player_data WHERE name IS NOT NULL AND total_xp > 0"
      );
      appendNameExclusion(sql, excludedLowerNames);

      try (PreparedStatement p = this.connection.prepareStatement(sql.toString())) {
         bindExcludedNames(p, 1, excludedLowerNames);

         try (ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
               all.add(new DatabaseManager.TopRodEntry(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getLong(4)));
            }
         }
      } catch (SQLException var14) {
         this.plugin.getLogger().warning("topByMaxRod: " + var14.getMessage());
      }

      all.sort((a, b) -> {
         int ta = tierIndex.applyAsInt(a.rodId());
         int tb = tierIndex.applyAsInt(b.rodId());
         if (ta != tb) {
            return Integer.compare(tb, ta);
         } else {
            return a.currentLevel() != b.currentLevel() ? Integer.compare(b.currentLevel(), a.currentLevel()) : Long.compare(b.totalXp(), a.totalXp());
         }
      });
      return all.size() > Math.max(1, limit) ? all.subList(0, Math.max(1, limit)) : all;
   }

   private static void appendNameExclusion(StringBuilder sql, Set<String> excludedLowerNames) {
      if (excludedLowerNames != null && !excludedLowerNames.isEmpty()) {
         sql.append(" AND LOWER(name) NOT IN (");

         for (int i = 0; i < excludedLowerNames.size(); i++) {
            if (i > 0) {
               sql.append(',');
            }

            sql.append('?');
         }

         sql.append(')');
      }
   }

   private static int bindExcludedNames(PreparedStatement p, int startIdx, Set<String> excludedLowerNames) throws SQLException {
      if (excludedLowerNames == null) {
         return startIdx;
      } else {
         int idx = startIdx;

         for (String n : excludedLowerNames) {
            p.setString(idx++, n);
         }

         return idx;
      }
   }

   public synchronized DatabaseManager.PlayerStats getStats(UUID uuid) {
      long tokens = 0L;
      long xp = 0L;
      long catches = 0L;
      long lastSeen = 0L;
      String name = null;
      String maxRod = null;

      try (PreparedStatement p = this.connection
            .prepareStatement("SELECT name, tokens, total_xp, total_catches, highest_max_rod, last_seen FROM player_data WHERE uuid=?")) {
         p.setString(1, uuid.toString());

         try (ResultSet rs = p.executeQuery()) {
            if (rs.next()) {
               name = rs.getString(1);
               tokens = rs.getLong(2);
               xp = rs.getLong(3);
               catches = rs.getLong(4);
               maxRod = rs.getString(5);
               lastSeen = rs.getLong(6);
            }
         }
      } catch (SQLException var20) {
         this.plugin.getLogger().warning("getStats: " + var20.getMessage());
      }

      return new DatabaseManager.PlayerStats(uuid, name, tokens, xp, catches, maxRod, lastSeen, this.getTierCatches(uuid), this.getPrestigeRecords(uuid));
   }

   public synchronized DatabaseManager.ChallengeProgress getChallengeProgress(UUID uuid, String challengeId) {
      try {
         DatabaseManager.ChallengeProgress var5;
         try (PreparedStatement p = this.connection
               .prepareStatement("SELECT progress, completed, last_reset FROM challenge_progress WHERE uuid=? AND challenge_id=?")) {
            p.setString(1, uuid.toString());
            p.setString(2, challengeId);

            try (ResultSet rs = p.executeQuery()) {
               if (!rs.next()) {
                  return new DatabaseManager.ChallengeProgress(0L, false, 0L);
               }

               var5 = new DatabaseManager.ChallengeProgress(rs.getLong(1), rs.getInt(2) != 0, rs.getLong(3));
            }
         }

         return var5;
      } catch (SQLException var11) {
         this.plugin.getLogger().warning("getChallengeProgress: " + var11.getMessage());
         return new DatabaseManager.ChallengeProgress(0L, false, 0L);
      }
   }

   public synchronized void setChallengeProgress(UUID uuid, String id, long progress, boolean completed, long resetAt) {
      try (PreparedStatement p = this.connection
            .prepareStatement(
               "INSERT INTO challenge_progress(uuid, challenge_id, progress, completed, last_reset)\nVALUES(?,?,?,?,?)\nON CONFLICT(uuid, challenge_id) DO UPDATE SET\n  progress=excluded.progress,\n  completed=excluded.completed,\n  last_reset=excluded.last_reset;"
            )) {
         p.setString(1, uuid.toString());
         p.setString(2, id);
         p.setLong(3, progress);
         p.setInt(4, completed ? 1 : 0);
         p.setLong(5, resetAt);
         p.executeUpdate();
      } catch (SQLException var13) {
         this.plugin.getLogger().warning("setChallengeProgress: " + var13.getMessage());
      }
   }

   public static record ChallengeProgress(long progress, boolean completed, long lastReset) {
   }

   public static record PlayerStats(
      UUID uuid,
      String name,
      long tokens,
      long totalXp,
      long totalCatches,
      String highestMaxRod,
      long lastSeen,
      EnumMap<RewardTier, Long> tierCatches,
      Map<String, Integer> prestige
   ) {
   }

   public static record TopEntry(String name, long totalXp, long totalCatches) {
   }

   public static record TopRodEntry(String name, String rodId, int currentLevel, long totalXp) {
   }

   public static record TopTokensEntry(String name, long tokens) {
   }

   public static record TopEarningsEntry(String name, long earned) {
   }
}
