package com.todogame.app.database

import android.util.Log
import com.todogame.app.models.*
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Calendar

object DatabaseHelper {
    private const val SERVER = "10.0.2.2"; private const val PORT = "1433"
    private const val INSTANCE = "SQLEXPRESS"; private const val DATABASE = "TodoGameDB"
    private const val USER = "todogame"; private const val PASSWORD = "TodoGame123!"
    private const val DRIVER = "net.sourceforge.jtds.jdbc.Driver"

    fun getConnection(): Connection {
        Class.forName(DRIVER)
        val url = "jdbc:jtds:sqlserver://$SERVER:$PORT/$DATABASE;instance=$INSTANCE;encrypt=false"
        return DriverManager.getConnection(url, USER, PASSWORD)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun initDatabase() {
        try {
            getConnection().use { conn ->
                val st = conn.createStatement()
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Users' AND xtype='U')
                    CREATE TABLE Users (Id INT IDENTITY PRIMARY KEY, Email NVARCHAR(100) UNIQUE NOT NULL,
                    Username NVARCHAR(50) NOT NULL, PasswordHash NVARCHAR(255) NOT NULL,
                    Role NVARCHAR(20) DEFAULT 'user', TaskStreak INT DEFAULT 0,
                    LastTaskDate DATE NULL, AvatarId INT DEFAULT 0)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Categories' AND xtype='U')
                    CREATE TABLE Categories (Id INT IDENTITY PRIMARY KEY, UserId INT NOT NULL,
                    Name NVARCHAR(100) NOT NULL, Color NVARCHAR(20) DEFAULT '#7C4DFF',
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Tasks' AND xtype='U')
                    CREATE TABLE Tasks (Id INT IDENTITY PRIMARY KEY, UserId INT NOT NULL,
                    CategoryId INT NULL, Title NVARCHAR(200) NOT NULL, Description NVARCHAR(MAX) NULL,
                    Status NVARCHAR(20) DEFAULT 'pending', Priority INT DEFAULT 2,
                    DueDate DATE NULL, CompletedAt DATETIME NULL, DeletedAt DATETIME NULL,
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
                    FOREIGN KEY (CategoryId) REFERENCES Categories(Id) ON DELETE SET NULL)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Achievements' AND xtype='U')
                    CREATE TABLE Achievements (Id INT IDENTITY PRIMARY KEY, Name NVARCHAR(100) NOT NULL,
                    Description NVARCHAR(300) NOT NULL, RequiredValue INT NOT NULL,
                    AchievementType NVARCHAR(50) NOT NULL, IconName NVARCHAR(50) NOT NULL, XpReward INT DEFAULT 0)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='UserAchievements' AND xtype='U')
                    CREATE TABLE UserAchievements (Id INT IDENTITY PRIMARY KEY, UserId INT NOT NULL,
                    AchievementId INT NOT NULL, UnlockedAt DATETIME DEFAULT GETDATE(),
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
                    FOREIGN KEY (AchievementId) REFERENCES Achievements(Id) ON DELETE CASCADE)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='MoodLogs' AND xtype='U')
                    CREATE TABLE MoodLogs (Id INT IDENTITY PRIMARY KEY, UserId INT NOT NULL,
                    Mood INT NOT NULL CHECK (Mood BETWEEN 1 AND 5), Note NVARCHAR(500) DEFAULT '',
                    LogDate DATE NOT NULL DEFAULT CAST(GETDATE() AS DATE),
                    TasksCompleted INT DEFAULT 0,
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Habits' AND xtype='U')
                    CREATE TABLE Habits (Id INT IDENTITY PRIMARY KEY, UserId INT NOT NULL,
                    Name NVARCHAR(100) NOT NULL, Color NVARCHAR(20) DEFAULT '#7C4DFF',
                    CurrentStreak INT DEFAULT 0, TotalDays INT DEFAULT 0,
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='HabitLogs' AND xtype='U')
                    CREATE TABLE HabitLogs (Id INT IDENTITY PRIMARY KEY, HabitId INT NOT NULL,
                    LogDate DATE NOT NULL, Completed BIT DEFAULT 1,
                    FOREIGN KEY (HabitId) REFERENCES Habits(Id) ON DELETE CASCADE)""")
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='DailyChallenges' AND xtype='U')
                    CREATE TABLE DailyChallenges (Id INT IDENTITY PRIMARY KEY, UserId INT NOT NULL,
                    ChallengeType NVARCHAR(50) NOT NULL, Description NVARCHAR(200) NOT NULL,
                    TargetValue INT DEFAULT 1, CurrentValue INT DEFAULT 0,
                    IsCompleted BIT DEFAULT 0, ChallengeDate DATE DEFAULT CAST(GETDATE() AS DATE),
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE)""")
                // Seed achievements
                val count = conn.createStatement().executeQuery("SELECT COUNT(*) FROM Achievements").also { it.next() }.getInt(1)
                if (count == 0) seedAchievements(conn)
                // Seed admin
                val adminCount = conn.createStatement().executeQuery("SELECT COUNT(*) FROM Users WHERE Email='admin@todogame.com'").also { it.next() }.getInt(1)
                if (adminCount == 0) {
                    conn.prepareStatement("INSERT INTO Users(Email,Username,PasswordHash,Role) VALUES(?,?,?,?)").also {
                        it.setString(1, "admin@todogame.com"); it.setString(2, "Admin")
                        it.setString(3, sha256("admin123")); it.setString(4, "admin"); it.executeUpdate()
                    }
                }
                // === Миграции колонок Users (безопасно, в try/catch) ===
                try { st.execute("ALTER TABLE Users ADD AvatarId INT DEFAULT 0") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD Xp INT DEFAULT 0") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD Coins INT DEFAULT 0") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD UserLevel INT DEFAULT 1") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD PetType NVARCHAR(20) DEFAULT 'fox'") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD PetName NVARCHAR(50) DEFAULT N'Лис'") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD PetHappiness INT DEFAULT 80") } catch (e: Exception) { }
                try { st.execute("ALTER TABLE Users ADD Theme NVARCHAR(20) DEFAULT 'light'") } catch (e: Exception) { }

                // === Новые таблицы ===
                // Друзья (двунаправленные связи; одна строка на пару + статус)
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Friendships' AND xtype='U')
                    CREATE TABLE Friendships (Id INT IDENTITY PRIMARY KEY,
                    RequesterId INT NOT NULL, AddresseeId INT NOT NULL,
                    Status NVARCHAR(20) DEFAULT 'pending',
                    CreatedAt DATETIME DEFAULT GETDATE(),
                    FOREIGN KEY (RequesterId) REFERENCES Users(Id),
                    FOREIGN KEY (AddresseeId) REFERENCES Users(Id))""")
                // Совместные задачи (какая задача кому расшарена)
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='TaskShares' AND xtype='U')
                    CREATE TABLE TaskShares (Id INT IDENTITY PRIMARY KEY,
                    TaskId INT NOT NULL, SharedWithUserId INT NOT NULL,
                    CreatedAt DATETIME DEFAULT GETDATE(),
                    FOREIGN KEY (TaskId) REFERENCES Tasks(Id) ON DELETE CASCADE,
                    FOREIGN KEY (SharedWithUserId) REFERENCES Users(Id))""")
                // Каталог товаров магазина
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='ShopItems' AND xtype='U')
                    CREATE TABLE ShopItems (Id INT IDENTITY PRIMARY KEY,
                    ItemType NVARCHAR(20) NOT NULL, Name NVARCHAR(100) NOT NULL,
                    IconValue NVARCHAR(50) NOT NULL, Price INT NOT NULL DEFAULT 0)""")
                // Купленные товары пользователей
                st.execute("""IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='UserItems' AND xtype='U')
                    CREATE TABLE UserItems (Id INT IDENTITY PRIMARY KEY,
                    UserId INT NOT NULL, ItemId INT NOT NULL,
                    PurchasedAt DATETIME DEFAULT GETDATE(),
                    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
                    FOREIGN KEY (ItemId) REFERENCES ShopItems(Id))""")

                // Наполнение магазина (один раз)
                val shopCount = conn.createStatement().executeQuery("SELECT COUNT(*) FROM ShopItems").also { it.next() }.getInt(1)
                if (shopCount == 0) seedShopItems(conn)
            }
        } catch (e: Exception) { Log.e("DB", "initDatabase error: ${e.message}") }
    }

    private fun seedAchievements(conn: Connection) {
        val achievements = listOf(
            Triple("Первый шаг", "Выполни первую задачу", "tasks_total:1"),
            Triple("Десятка", "Выполни 10 задач", "tasks_total:10"),
            Triple("Полсотни", "Выполни 50 задач", "tasks_total:50"),
            Triple("Продуктивный день", "5 задач за один день", "tasks_today:5"),
            Triple("Машина продуктивности", "10 задач за один день", "tasks_today:10"),
            Triple("3 дня подряд", "Серия 3 дня", "streak:3"),
            Triple("Неделя", "Серия 7 дней", "streak:7"),
            Triple("Месяц", "Серия 30 дней", "streak:30"),
            Triple("Организатор", "Создай 3 папки", "categories:3"),
            Triple("Пунктуальный", "10 задач до дедлайна", "before_deadline:10"),
            Triple("Без просрочек", "7 дней без просроченных задач", "no_overdue:7"),
            Triple("Настроение ОК", "Заполни трекер настроения 7 дней", "mood_streak:7"),
            Triple("Привычка", "Выполни привычку 14 дней подряд", "habit_streak:14"),
            Triple("Вызов принят", "Выполни 5 ежедневных вызовов", "challenges:5"),
            Triple("Легенда", "Выполни 100 задач", "tasks_total:100")
        )
        achievements.forEach { (name, desc, type) ->
            val parts = type.split(":"); val atype = parts[0]; val val_ = parts[1].toInt()
            conn.prepareStatement("INSERT INTO Achievements(Name,Description,RequiredValue,AchievementType,IconName,XpReward) VALUES(?,?,?,?,?,?)").also {
                it.setString(1, name); it.setString(2, desc); it.setInt(3, val_)
                it.setString(4, atype); it.setString(5, atype); it.setInt(6, val_ * 5)
                it.executeUpdate()
            }
        }
    }

    // ========== МАГАЗИН: наполнение каталога ==========
    private fun seedShopItems(conn: Connection) {
        val items = listOf(
            // type, name, iconValue, price
            Triple("pet", "Котёнок", "cat"),
            Triple("pet", "Панда", "panda"),
            Triple("pet", "Дракончик", "dragon"),
            Triple("theme", "Тёмная тема", "dark"),
            Triple("badge", "Новичок", "🌱"),
            Triple("badge", "Трудяга", "💪"),
            Triple("badge", "Легенда", "👑"),
            Triple("badge", "Звезда", "⭐"),
            Triple("avatar", "Орёл", "🦅"),
            Triple("avatar", "Единорог", "🦄"),
            Triple("avatar", "Дракон", "🐲"),
            Triple("avatar", "Молния", "⚡")
        )
        val prices = mapOf("pet" to 150, "theme" to 100, "badge" to 80, "avatar" to 50)
        conn.prepareStatement("INSERT INTO ShopItems(ItemType,Name,IconValue,Price) VALUES(?,?,?,?)").use { ps ->
            for ((type, name, icon) in items) {
                ps.setString(1, type); ps.setString(2, name)
                ps.setString(3, icon); ps.setInt(4, prices[type] ?: 50)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    // ========== ГЕЙМИФИКАЦИЯ: начисление опыта и монет ==========
    // Вызывается при выполнении задачи. Возвращает true, если повысился уровень.
    fun grantRewards(userId: Int, xpGain: Int, coinGain: Int): Boolean {
        var leveledUp = false
        try {
            getConnection().use { conn ->
                var oldXp = 0
                conn.prepareStatement("SELECT Xp FROM Users WHERE Id=?").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); if (rs.next()) oldXp = rs.getInt(1)
                }
                val newXp = oldXp + xpGain
                val oldLevel = LevelData.levelForXp(oldXp)
                val newLevel = LevelData.levelForXp(newXp)
                leveledUp = newLevel > oldLevel
                conn.prepareStatement("UPDATE Users SET Xp=?, UserLevel=?, Coins=Coins+? WHERE Id=?").use { ps ->
                    ps.setInt(1, newXp); ps.setInt(2, newLevel)
                    ps.setInt(3, coinGain); ps.setInt(4, userId); ps.executeUpdate()
                }
            }
        } catch (e: Exception) { Log.e("DB", "grantRewards: ${e.message}") }
        return leveledUp
    }

    // ========== ПИТОМЕЦ: пересчёт настроения ==========
    // Настроение 0..100. Зависит от серии и активности за сегодня.
    fun updatePetHappiness(userId: Int): Int {
        var happiness = 50
        try {
            getConnection().use { conn ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                var streak = 0; var todayDone = 0; var todayPending = 0
                conn.prepareStatement("SELECT TaskStreak FROM Users WHERE Id=?").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); if (rs.next()) streak = rs.getInt(1)
                }
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed' AND CAST(CompletedAt AS DATE)=?").use { ps ->
                    ps.setInt(1, userId); ps.setString(2, today); val rs = ps.executeQuery(); if (rs.next()) todayDone = rs.getInt(1)
                }
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='pending' AND DeletedAt IS NULL").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); if (rs.next()) todayPending = rs.getInt(1)
                }
                // База от серии (до 40), бонус за выполненное сегодня (до 40), штраф за гору невыполненного
                happiness = 40
                happiness += minOf(streak * 4, 30)
                happiness += minOf(todayDone * 10, 40)
                if (todayDone == 0 && todayPending > 3) happiness -= 25
                happiness = happiness.coerceIn(0, 100)
                conn.prepareStatement("UPDATE Users SET PetHappiness=? WHERE Id=?").use { ps ->
                    ps.setInt(1, happiness); ps.setInt(2, userId); ps.executeUpdate()
                }
            }
        } catch (e: Exception) { Log.e("DB", "updatePetHappiness: ${e.message}") }
        return happiness
    }

    fun getPetInfo(userId: Int): Triple<String, String, Int> {
        var type = "fox"; var name = "Лис"; var happiness = 80
        try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT PetType, PetName, PetHappiness FROM Users WHERE Id=?").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery()
                    if (rs.next()) { type = rs.getString(1) ?: "fox"; name = rs.getString(2) ?: "Лис"; happiness = rs.getInt(3) }
                }
            }
        } catch (e: Exception) { Log.e("DB", "getPetInfo: ${e.message}") }
        return Triple(type, name, happiness)
    }

    fun setPet(userId: Int, type: String, name: String) {
        try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Users SET PetType=?, PetName=? WHERE Id=?").use { ps ->
                    ps.setString(1, type); ps.setString(2, name); ps.setInt(3, userId); ps.executeUpdate()
                }
            }
        } catch (e: Exception) { Log.e("DB", "setPet: ${e.message}") }
    }

    // Пересчитать настроение и сразу вернуть актуальные данные питомца
    fun updatePetHappinessAndGet(userId: Int): Triple<String, String, Int> {
        updatePetHappiness(userId)
        return getPetInfo(userId)
    }

    // Типы питомцев, купленных пользователем в магазине (ItemType='pet')
    fun getOwnedPetTypes(userId: Int): List<String> {
        val list = mutableListOf<String>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("""SELECT s.IconValue FROM UserItems ui
                    JOIN ShopItems s ON s.Id=ui.ItemId
                    WHERE ui.UserId=? AND s.ItemType='pet'""").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery()
                    while (rs.next()) list.add(rs.getString(1))
                }
            }
        } catch (e: Exception) { Log.e("DB", "getOwnedPetTypes: ${e.message}") }
        return list
    }

    // Купленные аватары: возвращает индексы (8..11) для покупных аватаров.
    // Сопоставление: товары ItemType='avatar' идут по порядку -> индексы 8,9,10,11.
    fun getOwnedAvatarIndices(userId: Int): Set<Int> {
        val owned = mutableSetOf<Int>()
        try {
            getConnection().use { conn ->
                // Порядок покупных аватаров в магазине
                val avatarOrder = mutableListOf<Int>()
                conn.prepareStatement("SELECT Id FROM ShopItems WHERE ItemType='avatar' ORDER BY Id").use { ps ->
                    val rs = ps.executeQuery(); while (rs.next()) avatarOrder.add(rs.getInt(1))
                }
                val ownedIds = mutableSetOf<Int>()
                conn.prepareStatement("""SELECT s.Id FROM UserItems ui JOIN ShopItems s ON s.Id=ui.ItemId
                    WHERE ui.UserId=? AND s.ItemType='avatar'""").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); while (rs.next()) ownedIds.add(rs.getInt(1))
                }
                // индекс покупного аватара = 8 + позиция в каталоге
                avatarOrder.forEachIndexed { i, id -> if (ownedIds.contains(id)) owned.add(8 + i) }
            }
        } catch (e: Exception) { Log.e("DB", "getOwnedAvatarIndices: ${e.message}") }
        return owned
    }

    // ========== СТАТИСТИКА ПРОДУКТИВНОСТИ ==========
    // Выполненные задачи за последние 7 дней (метка дня -> кол-во)
    fun getWeeklyTaskStats(userId: Int): List<StatPoint> {
        val result = mutableListOf<StatPoint>()
        try {
            getConnection().use { conn ->
                val dayNames = listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DATE, -6)
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd")
                for (i in 0..6) {
                    val date = fmt.format(cal.time)
                    var cnt = 0
                    conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed' AND CAST(CompletedAt AS DATE)=?").use { ps ->
                        ps.setInt(1, userId); ps.setString(2, date); val rs = ps.executeQuery(); if (rs.next()) cnt = rs.getInt(1)
                    }
                    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
                    val label = dayNames[(dow + 5) % 7]
                    result.add(StatPoint(label, cnt))
                    cal.add(java.util.Calendar.DATE, 1)
                }
            }
        } catch (e: Exception) { Log.e("DB", "getWeeklyTaskStats: ${e.message}") }
        return result
    }

    // Сводка для экрана статистики
    fun getProductivityStats(userId: Int): Map<String, Int> {
        val m = mutableMapOf<String, Int>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed'").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) m["total_done"]=rs.getInt(1) }
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed' AND CompletedAt >= DATEADD(day,-7,GETDATE())").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) m["week_done"]=rs.getInt(1) }
                conn.prepareStatement("SELECT TaskStreak, Xp, Coins, UserLevel FROM Users WHERE Id=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()){ m["streak"]=rs.getInt(1); m["xp"]=rs.getInt(2); m["coins"]=rs.getInt(3); m["level"]=rs.getInt(4) } }
                // Лучший день недели (по выполненным за всё время)
                conn.prepareStatement("""SELECT TOP 1 DATEPART(weekday, CompletedAt) AS dow, COUNT(*) AS c
                    FROM Tasks WHERE UserId=? AND Status='completed' AND CompletedAt IS NOT NULL
                    GROUP BY DATEPART(weekday, CompletedAt) ORDER BY c DESC""").use { ps ->
                    ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) m["best_dow"]=rs.getInt(1)
                }
            }
        } catch (e: Exception) { Log.e("DB", "getProductivityStats: ${e.message}") }
        return m
    }

    // ========== МАГАЗИН ==========
    fun getShopItems(userId: Int): List<ShopItem> {
        val list = mutableListOf<ShopItem>()
        try {
            getConnection().use { conn ->
                val owned = mutableSetOf<Int>()
                conn.prepareStatement("SELECT ItemId FROM UserItems WHERE UserId=?").use { ps ->
                    ps.setInt(1,userId); val rs=ps.executeQuery(); while(rs.next()) owned.add(rs.getInt(1))
                }
                conn.prepareStatement("SELECT Id,ItemType,Name,IconValue,Price FROM ShopItems ORDER BY ItemType, Price").use { ps ->
                    val rs=ps.executeQuery()
                    while(rs.next()) {
                        val id=rs.getInt(1)
                        list.add(ShopItem(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), owned.contains(id)))
                    }
                }
            }
        } catch (e: Exception) { Log.e("DB", "getShopItems: ${e.message}") }
        return list
    }

    // Покупка. Возвращает: 0=успех, 1=мало монет, 2=уже куплено, 3=ошибка
    fun buyItem(userId: Int, itemId: Int): Int {
        try {
            getConnection().use { conn ->
                var price = -1
                conn.prepareStatement("SELECT Price FROM ShopItems WHERE Id=?").use { ps -> ps.setInt(1,itemId); val rs=ps.executeQuery(); if(rs.next()) price=rs.getInt(1) }
                if (price < 0) return 3
                var alreadyOwned = false
                conn.prepareStatement("SELECT COUNT(*) FROM UserItems WHERE UserId=? AND ItemId=?").use { ps -> ps.setInt(1,userId); ps.setInt(2,itemId); val rs=ps.executeQuery(); if(rs.next()) alreadyOwned = rs.getInt(1)>0 }
                if (alreadyOwned) return 2
                var coins = 0
                conn.prepareStatement("SELECT Coins FROM Users WHERE Id=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) coins=rs.getInt(1) }
                if (coins < price) return 1
                conn.prepareStatement("UPDATE Users SET Coins=Coins-? WHERE Id=?").use { ps -> ps.setInt(1,price); ps.setInt(2,userId); ps.executeUpdate() }
                conn.prepareStatement("INSERT INTO UserItems(UserId,ItemId) VALUES(?,?)").use { ps -> ps.setInt(1,userId); ps.setInt(2,itemId); ps.executeUpdate() }
            }
            return 0
        } catch (e: Exception) { Log.e("DB", "buyItem: ${e.message}"); return 3 }
    }

    fun getCoins(userId: Int): Int {
        try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT Coins FROM Users WHERE Id=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) return rs.getInt(1) }
            }
        } catch (e: Exception) { Log.e("DB","getCoins: ${e.message}") }
        return 0
    }

    // ========== ДРУЗЬЯ ==========
    // Поиск пользователей по имени (кроме себя)
    fun searchUsers(query: String, selfId: Int): List<Friend> {
        val list = mutableListOf<Friend>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT TOP 20 Id,Username,AvatarId,UserLevel,TaskStreak FROM Users WHERE Username LIKE ? AND Id<>? AND Role<>'admin'").use { ps ->
                    ps.setString(1, "%$query%"); ps.setInt(2, selfId); val rs=ps.executeQuery()
                    while(rs.next()) list.add(Friend(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)))
                }
            }
        } catch (e: Exception) { Log.e("DB","searchUsers: ${e.message}") }
        return list
    }

    // Отправить заявку в друзья. 0=ок, 1=уже есть связь, 2=ошибка
    fun sendFriendRequest(fromId: Int, toId: Int): Int {
        try {
            getConnection().use { conn ->
                var exists = false
                conn.prepareStatement("""SELECT COUNT(*) FROM Friendships
                    WHERE (RequesterId=? AND AddresseeId=?) OR (RequesterId=? AND AddresseeId=?)""").use { ps ->
                    ps.setInt(1,fromId); ps.setInt(2,toId); ps.setInt(3,toId); ps.setInt(4,fromId)
                    val rs=ps.executeQuery(); if(rs.next()) exists = rs.getInt(1)>0
                }
                if (exists) return 1
                conn.prepareStatement("INSERT INTO Friendships(RequesterId,AddresseeId,Status) VALUES(?,?,'pending')").use { ps ->
                    ps.setInt(1,fromId); ps.setInt(2,toId); ps.executeUpdate()
                }
            }
            return 0
        } catch (e: Exception) { Log.e("DB","sendFriendRequest: ${e.message}"); return 2 }
    }

    // Входящие заявки
    fun getFriendRequests(userId: Int): List<FriendRequest> {
        val list = mutableListOf<FriendRequest>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("""SELECT f.Id, u.Id, u.Username, u.AvatarId FROM Friendships f
                    JOIN Users u ON u.Id=f.RequesterId
                    WHERE f.AddresseeId=? AND f.Status='pending'""").use { ps ->
                    ps.setInt(1,userId); val rs=ps.executeQuery()
                    while(rs.next()) list.add(FriendRequest(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4)))
                }
            }
        } catch (e: Exception) { Log.e("DB","getFriendRequests: ${e.message}") }
        return list
    }

    fun respondFriendRequest(requestId: Int, accept: Boolean) {
        try {
            getConnection().use { conn ->
                if (accept) {
                    conn.prepareStatement("UPDATE Friendships SET Status='accepted' WHERE Id=?").use { ps -> ps.setInt(1,requestId); ps.executeUpdate() }
                } else {
                    conn.prepareStatement("DELETE FROM Friendships WHERE Id=?").use { ps -> ps.setInt(1,requestId); ps.executeUpdate() }
                }
            }
        } catch (e: Exception) { Log.e("DB","respondFriendRequest: ${e.message}") }
    }

    // Список друзей (принятые связи в обе стороны)
    fun getFriends(userId: Int): List<Friend> {
        val list = mutableListOf<Friend>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("""SELECT u.Id,u.Username,u.AvatarId,u.UserLevel,u.TaskStreak FROM Friendships f
                    JOIN Users u ON (u.Id = CASE WHEN f.RequesterId=? THEN f.AddresseeId ELSE f.RequesterId END)
                    WHERE (f.RequesterId=? OR f.AddresseeId=?) AND f.Status='accepted'""").use { ps ->
                    ps.setInt(1,userId); ps.setInt(2,userId); ps.setInt(3,userId); val rs=ps.executeQuery()
                    while(rs.next()) list.add(Friend(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), "accepted"))
                }
            }
        } catch (e: Exception) { Log.e("DB","getFriends: ${e.message}") }
        return list
    }

    fun removeFriend(userId: Int, friendId: Int) {
        try {
            getConnection().use { conn ->
                conn.prepareStatement("""DELETE FROM Friendships
                    WHERE (RequesterId=? AND AddresseeId=?) OR (RequesterId=? AND AddresseeId=?)""").use { ps ->
                    ps.setInt(1,userId); ps.setInt(2,friendId); ps.setInt(3,friendId); ps.setInt(4,userId); ps.executeUpdate()
                }
            }
        } catch (e: Exception) { Log.e("DB","removeFriend: ${e.message}") }
    }

    // Статистика друга (для просмотра)
    fun getFriendStats(friendId: Int): FriendStats {
        var stats = FriendStats()
        try {
            getConnection().use { conn ->
                var username=""; var avatarId=0; var level=1; var xp=0; var streak=0; var done=0; var ach=0
                conn.prepareStatement("SELECT Username,AvatarId,UserLevel,Xp,TaskStreak FROM Users WHERE Id=?").use { ps ->
                    ps.setInt(1,friendId); val rs=ps.executeQuery()
                    if(rs.next()){ username=rs.getString(1); avatarId=rs.getInt(2); level=rs.getInt(3); xp=rs.getInt(4); streak=rs.getInt(5) }
                }
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed'").use { ps -> ps.setInt(1,friendId); val rs=ps.executeQuery(); if(rs.next()) done=rs.getInt(1) }
                conn.prepareStatement("SELECT COUNT(*) FROM UserAchievements WHERE UserId=?").use { ps -> ps.setInt(1,friendId); val rs=ps.executeQuery(); if(rs.next()) ach=rs.getInt(1) }
                stats = FriendStats(username, avatarId, level, xp, streak, done, ach)
            }
        } catch (e: Exception) { Log.e("DB","getFriendStats: ${e.message}") }
        return stats
    }

    // ========== СОВМЕСТНЫЕ ЗАДАЧИ ==========
    fun shareTask(taskId: Int, friendId: Int): Boolean {
        try {
            getConnection().use { conn ->
                var exists=false
                conn.prepareStatement("SELECT COUNT(*) FROM TaskShares WHERE TaskId=? AND SharedWithUserId=?").use { ps -> ps.setInt(1,taskId); ps.setInt(2,friendId); val rs=ps.executeQuery(); if(rs.next()) exists=rs.getInt(1)>0 }
                if (exists) return false
                conn.prepareStatement("INSERT INTO TaskShares(TaskId,SharedWithUserId) VALUES(?,?)").use { ps -> ps.setInt(1,taskId); ps.setInt(2,friendId); ps.executeUpdate() }
            }
            return true
        } catch (e: Exception) { Log.e("DB","shareTask: ${e.message}"); return false }
    }

    // Задачи, расшаренные данному пользователю (от друзей)
    fun getSharedTasks(userId: Int): List<Task> {
        val list = mutableListOf<Task>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("""SELECT t.Id,t.UserId,t.Title,t.Description,t.Status,t.Priority,
                    CONVERT(NVARCHAR,t.DueDate,23), t.CategoryId, u.Username
                    FROM TaskShares ts JOIN Tasks t ON t.Id=ts.TaskId
                    JOIN Users u ON u.Id=t.UserId
                    WHERE ts.SharedWithUserId=? AND t.DeletedAt IS NULL
                    ORDER BY t.Priority DESC""").use { ps ->
                    ps.setInt(1,userId); val rs=ps.executeQuery()
                    while(rs.next()) {
                        list.add(Task(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4) ?: "",
                            rs.getString(5), rs.getInt(6), rs.getString(7), 
                            rs.getInt(8).let { if (rs.wasNull()) null else it }, null, null,
                            rs.getInt(2), rs.getString(9)))
                    }
                }
            }
        } catch (e: Exception) { Log.e("DB","getSharedTasks: ${e.message}") }
        return list
    }

    // ========== AUTH ==========
    fun loginUser(email: String, password: String): User? {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT Id,Email,Username,Role,TaskStreak,LastTaskDate,AvatarId,Xp,Coins,UserLevel,PetType,PetName,PetHappiness,Theme FROM Users WHERE Email=? AND PasswordHash=?").use { ps ->
                    ps.setString(1, email.trim()); ps.setString(2, sha256(password))
                    val rs = ps.executeQuery()
                    if (rs.next()) User(
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getString(6), rs.getInt(7),
                        rs.getInt(8), rs.getInt(9), rs.getInt(10),
                        rs.getString(11) ?: "fox", rs.getString(12) ?: "Лис", rs.getInt(13),
                        rs.getString(14) ?: "light"
                    ) else null
                }
            }
        } catch (e: Exception) { Log.e("DB", "loginUser: ${e.message}"); null }
    }

    fun registerUser(email: String, username: String, password: String): Boolean {
        return try {
            getConnection().use { conn ->
                val check = conn.prepareStatement("SELECT COUNT(*) FROM Users WHERE Email=?").use { ps -> ps.setString(1, email.trim()); val rs = ps.executeQuery(); rs.next(); rs.getInt(1) }
                if (check > 0) return false
                conn.prepareStatement("INSERT INTO Users(Email,Username,PasswordHash) VALUES(?,?,?)").use { ps ->
                    ps.setString(1, email.trim()); ps.setString(2, username.trim()); ps.setString(3, sha256(password)); ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { Log.e("DB", "registerUser: ${e.message}"); false }
    }

    // ========== TASKS ==========
    fun getTasks(userId: Int): List<Task> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT t.Id,t.UserId,t.Title,t.Description,t.Status,t.Priority,t.DueDate,t.CategoryId,c.Name,t.CompletedAt FROM Tasks t LEFT JOIN Categories c ON t.CategoryId=c.Id WHERE t.UserId=? AND t.Status='pending' ORDER BY t.Priority DESC,t.DueDate ASC").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); val list = mutableListOf<Task>()
                    while (rs.next()) list.add(Task(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)?:"",rs.getString(5),rs.getInt(6),rs.getString(7),rs.getObject(8) as? Int,rs.getString(9),rs.getString(10)))
                    list
                }
            }
        } catch (e: Exception) { Log.e("DB", "getTasks: ${e.message}"); emptyList() }
    }

    fun getTasksByCategory(userId: Int, categoryId: Int): List<Task> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT t.Id,t.UserId,t.Title,t.Description,t.Status,t.Priority,t.DueDate,t.CategoryId,c.Name,t.CompletedAt FROM Tasks t LEFT JOIN Categories c ON t.CategoryId=c.Id WHERE t.UserId=? AND t.CategoryId=? AND t.Status='pending' ORDER BY t.Priority DESC").use { ps ->
                    ps.setInt(1, userId); ps.setInt(2, categoryId); val rs = ps.executeQuery(); val list = mutableListOf<Task>()
                    while (rs.next()) list.add(Task(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)?:"",rs.getString(5),rs.getInt(6),rs.getString(7),rs.getObject(8) as? Int,rs.getString(9),rs.getString(10)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun getTrashTasks(userId: Int): List<Task> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT t.Id,t.UserId,t.Title,t.Description,t.Status,t.Priority,t.DueDate,t.CategoryId,c.Name,t.CompletedAt FROM Tasks t LEFT JOIN Categories c ON t.CategoryId=c.Id WHERE t.UserId=? AND t.Status='trash' ORDER BY t.DeletedAt DESC").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); val list = mutableListOf<Task>()
                    while (rs.next()) list.add(Task(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)?:"",rs.getString(5),rs.getInt(6),rs.getString(7),rs.getObject(8) as? Int,rs.getString(9),rs.getString(10)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun createTask(userId: Int, title: String, description: String, priority: Int, dueDate: String?, categoryId: Int?): Task? {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("INSERT INTO Tasks(UserId,Title,Description,Priority,DueDate,CategoryId) VALUES(?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setInt(1, userId); ps.setString(2, title); ps.setString(3, description)
                    ps.setInt(4, priority); ps.setString(5, dueDate)
                    if (categoryId != null) ps.setInt(6, categoryId) else ps.setNull(6, java.sql.Types.INTEGER)
                    ps.executeUpdate()
                    val keys = ps.generatedKeys; if (keys.next()) Task(keys.getInt(1), userId, title, description, "pending", priority, dueDate, categoryId) else null
                }
            }
        } catch (e: Exception) { Log.e("DB", "createTask: ${e.message}"); null }
    }

    fun updateTask(taskId: Int, title: String, description: String, priority: Int, dueDate: String?, categoryId: Int?): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Tasks SET Title=?,Description=?,Priority=?,DueDate=?,CategoryId=? WHERE Id=?").use { ps ->
                    ps.setString(1, title); ps.setString(2, description); ps.setInt(3, priority)
                    ps.setString(4, dueDate)
                    if (categoryId != null) ps.setInt(5, categoryId) else ps.setNull(5, java.sql.Types.INTEGER)
                    ps.setInt(6, taskId); ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { false }
    }

    fun completeTask(taskId: Int, userId: Int): Pair<Int, List<Achievement>> {
        return try {
            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement("UPDATE Tasks SET Status='completed',CompletedAt=GETDATE() WHERE Id=? AND UserId=?").use { it.setInt(1,taskId); it.setInt(2,userId); it.executeUpdate() }
                    // Update streak
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                    val userRs = conn.prepareStatement("SELECT TaskStreak,LastTaskDate FROM Users WHERE Id=?").use { ps -> ps.setInt(1,userId); ps.executeQuery() }
                    var streak = 0
                    if (userRs.next()) {
                        val lastDate = userRs.getString(2)
                        val prevStreak = userRs.getInt(1)
                        val cal = Calendar.getInstance(); cal.add(Calendar.DATE, -1)
                        val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.time)
                        streak = when (lastDate) {
                            today -> prevStreak
                            yesterday -> prevStreak + 1
                            else -> 1
                        }
                    }
                    conn.prepareStatement("UPDATE Users SET TaskStreak=?,LastTaskDate=? WHERE Id=?").use { ps -> ps.setInt(1,streak); ps.setString(2,today); ps.setInt(3,userId); ps.executeUpdate() }
                    // Update daily challenges
                    updateChallengeProgress(conn, userId, "complete_tasks", 1)
                    updateChallengeProgress(conn, userId, "complete_before_deadline", 1)
                    // Награды за выполнение задачи: опыт и монеты (зависят от приоритета)
                    var priority = 2
                    conn.prepareStatement("SELECT Priority FROM Tasks WHERE Id=?").use { ps -> ps.setInt(1,taskId); val rs=ps.executeQuery(); if(rs.next()) priority=rs.getInt(1) }
                    val xpGain = 5 + priority * 5      // низкий=10, средний=15, высокий=20
                    val coinGain = 2 + priority * 2    // 4 / 6 / 8
                    val newXp = run {
                        var oldXp = 0
                        conn.prepareStatement("SELECT Xp FROM Users WHERE Id=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) oldXp=rs.getInt(1) }
                        oldXp + xpGain
                    }
                    val newLevel = LevelData.levelForXp(newXp)
                    conn.prepareStatement("UPDATE Users SET Xp=?, UserLevel=?, Coins=Coins+? WHERE Id=?").use { ps ->
                        ps.setInt(1,newXp); ps.setInt(2,newLevel); ps.setInt(3,coinGain); ps.setInt(4,userId); ps.executeUpdate()
                    }
                    conn.commit()
                    // Пересчёт настроения питомца (вне транзакции)
                    updatePetHappiness(userId)
                    val newAchievements = checkAndGrantAchievements(userId)
                    Pair(streak, newAchievements)
                } catch (e: Exception) { conn.rollback(); Pair(0, emptyList()) }
                finally { conn.autoCommit = true }
            }
        } catch (e: Exception) { Pair(0, emptyList()) }
    }

    fun moveToTrash(taskId: Int, userId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Tasks SET Status='trash',DeletedAt=GETDATE() WHERE Id=? AND UserId=?").use { ps -> ps.setInt(1,taskId); ps.setInt(2,userId); ps.executeUpdate() > 0 }
            }
        } catch (e: Exception) { false }
    }

    fun restoreTask(taskId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Tasks SET Status='pending',DeletedAt=NULL WHERE Id=?").use { ps -> ps.setInt(1,taskId); ps.executeUpdate() > 0 }
            }
        } catch (e: Exception) { false }
    }

    fun deleteTaskPermanently(taskId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM Tasks WHERE Id=?").use { ps -> ps.setInt(1,taskId); ps.executeUpdate() > 0 }
            }
        } catch (e: Exception) { false }
    }

    // ========== CATEGORIES ==========
    fun getCategories(userId: Int): List<Category> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT Id,UserId,Name,Color FROM Categories WHERE UserId=? ORDER BY Name").use { ps ->
                    ps.setInt(1, userId); val rs = ps.executeQuery(); val list = mutableListOf<Category>()
                    while (rs.next()) list.add(Category(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun createCategory(userId: Int, name: String, color: String): Category? {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("INSERT INTO Categories(UserId,Name,Color) VALUES(?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setInt(1,userId); ps.setString(2,name); ps.setString(3,color); ps.executeUpdate()
                    val keys = ps.generatedKeys; if (keys.next()) Category(keys.getInt(1),userId,name,color) else null
                }
            }
        } catch (e: Exception) { null }
    }

    fun deleteCategory(categoryId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Tasks SET CategoryId=NULL WHERE CategoryId=?").use { it.setInt(1,categoryId); it.executeUpdate() }
                conn.prepareStatement("DELETE FROM Categories WHERE Id=?").use { ps -> ps.setInt(1,categoryId); ps.executeUpdate() > 0 }
            }
        } catch (e: Exception) { false }
    }

    // ========== ACHIEVEMENTS ==========
    fun getUserAchievements(userId: Int): List<Achievement> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT a.Id,a.Name,a.Description,a.IconName,CASE WHEN ua.Id IS NOT NULL THEN 1 ELSE 0 END,ua.UnlockedAt FROM Achievements a LEFT JOIN UserAchievements ua ON a.Id=ua.AchievementId AND ua.UserId=? ORDER BY CASE WHEN ua.Id IS NOT NULL THEN 0 ELSE 1 END,a.Id").use { ps ->
                    ps.setInt(1,userId); val rs = ps.executeQuery(); val list = mutableListOf<Achievement>()
                    while (rs.next()) list.add(Achievement(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)==1,rs.getString(6)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun checkAndGrantAchievements(userId: Int): List<Achievement> {
        val newOnes = mutableListOf<Achievement>()
        try {
            getConnection().use { conn ->
                val stats = getUserStats(userId, conn)
                val pending = conn.prepareStatement("SELECT a.Id,a.Name,a.Description,a.AchievementType,a.RequiredValue,a.IconName FROM Achievements a WHERE a.Id NOT IN (SELECT AchievementId FROM UserAchievements WHERE UserId=?)").use { ps ->
                    ps.setInt(1,userId); val rs = ps.executeQuery(); val list = mutableListOf<Achievement>()
                    while (rs.next()) list.add(Achievement(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(6)))
                    list
                }
                pending.forEach { ach ->
                    val type = conn.prepareStatement("SELECT AchievementType,RequiredValue FROM Achievements WHERE Id=?").use { ps -> ps.setInt(1,ach.id); val rs=ps.executeQuery(); if(rs.next()) Pair(rs.getString(1),rs.getInt(2)) else null }
                    if (type != null) {
                        val (atype, required) = type
                        val current = when (atype) {
                            "tasks_total" -> stats["tasks_total"] ?: 0
                            "tasks_today" -> stats["tasks_today"] ?: 0
                            "streak" -> stats["streak"] ?: 0
                            "categories" -> stats["categories"] ?: 0
                            "mood_streak" -> stats["mood_streak"] ?: 0
                            else -> 0
                        }
                        if (current >= required) {
                            conn.prepareStatement("INSERT INTO UserAchievements(UserId,AchievementId) VALUES(?,?)").use { ps -> ps.setInt(1,userId); ps.setInt(2,ach.id); ps.executeUpdate() }
                            newOnes.add(ach)
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("DB","checkAchievements: ${e.message}") }
        return newOnes
    }

    private fun getUserStats(userId: Int, conn: Connection): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
            conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed'").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) stats["tasks_total"]=rs.getInt(1) }
            conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed' AND CAST(CompletedAt AS DATE)=?").use { ps -> ps.setInt(1,userId); ps.setString(2,today); val rs=ps.executeQuery(); if(rs.next()) stats["tasks_today"]=rs.getInt(1) }
            conn.prepareStatement("SELECT TaskStreak FROM Users WHERE Id=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) stats["streak"]=rs.getInt(1) }
            conn.prepareStatement("SELECT COUNT(*) FROM Categories WHERE UserId=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) stats["categories"]=rs.getInt(1) }
            conn.prepareStatement("SELECT COUNT(*) FROM MoodLogs WHERE UserId=? AND LogDate >= DATEADD(day,-7,GETDATE())").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) stats["mood_streak"]=rs.getInt(1) }
        } catch (e: Exception) { }
        return stats
    }

    // ========== MOOD ==========
    fun saveMood(userId: Int, mood: Int, note: String): Boolean {
        return try {
            getConnection().use { conn ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                val tasksToday = conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed' AND CAST(CompletedAt AS DATE)=?").use { ps -> ps.setInt(1,userId); ps.setString(2,today); val rs=ps.executeQuery(); rs.next(); rs.getInt(1) }
                val exists = conn.prepareStatement("SELECT COUNT(*) FROM MoodLogs WHERE UserId=? AND LogDate=?").use { ps -> ps.setInt(1,userId); ps.setString(2,today); val rs=ps.executeQuery(); rs.next(); rs.getInt(1) > 0 }
                if (exists) {
                    conn.prepareStatement("UPDATE MoodLogs SET Mood=?,Note=?,TasksCompleted=? WHERE UserId=? AND LogDate=?").use { ps -> ps.setInt(1,mood); ps.setString(2,note); ps.setInt(3,tasksToday); ps.setInt(4,userId); ps.setString(5,today); ps.executeUpdate() > 0 }
                } else {
                    conn.prepareStatement("INSERT INTO MoodLogs(UserId,Mood,Note,LogDate,TasksCompleted) VALUES(?,?,?,?,?)").use { ps -> ps.setInt(1,userId); ps.setInt(2,mood); ps.setString(3,note); ps.setString(4,today); ps.setInt(5,tasksToday); ps.executeUpdate() > 0 }
                }
            }
        } catch (e: Exception) { false }
    }

    fun getMoodLogs(userId: Int, days: Int = 30): List<MoodLog> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT Id,UserId,Mood,Note,CAST(LogDate AS NVARCHAR),TasksCompleted FROM MoodLogs WHERE UserId=? AND LogDate >= DATEADD(day,-?,GETDATE()) ORDER BY LogDate ASC").use { ps ->
                    ps.setInt(1,userId); ps.setInt(2,days); val rs=ps.executeQuery(); val list=mutableListOf<MoodLog>()
                    while(rs.next()) list.add(MoodLog(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getString(4)?:"",rs.getString(5)?:"",rs.getInt(6)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun hasMoodToday(userId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                conn.prepareStatement("SELECT COUNT(*) FROM MoodLogs WHERE UserId=? AND LogDate=?").use { ps -> ps.setInt(1,userId); ps.setString(2,today); val rs=ps.executeQuery(); rs.next(); rs.getInt(1) > 0 }
            }
        } catch (e: Exception) { false }
    }

    // ========== HABITS ==========
    fun getHabits(userId: Int): List<Habit> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT Id,UserId,Name,Color,CurrentStreak,TotalDays FROM Habits WHERE UserId=? ORDER BY Name").use { ps ->
                    ps.setInt(1,userId); val rs=ps.executeQuery(); val list=mutableListOf<Habit>()
                    while(rs.next()) list.add(Habit(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun createHabit(userId: Int, name: String, color: String): Habit? {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("INSERT INTO Habits(UserId,Name,Color) VALUES(?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setInt(1,userId); ps.setString(2,name); ps.setString(3,color); ps.executeUpdate()
                    val keys=ps.generatedKeys; if(keys.next()) Habit(keys.getInt(1),userId,name,color) else null
                }
            }
        } catch (e: Exception) { null }
    }

    fun logHabit(habitId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                val exists = conn.prepareStatement("SELECT COUNT(*) FROM HabitLogs WHERE HabitId=? AND LogDate=?").use { ps -> ps.setInt(1,habitId); ps.setString(2,today); val rs=ps.executeQuery(); rs.next(); rs.getInt(1) > 0 }
                if (exists) return true
                conn.prepareStatement("INSERT INTO HabitLogs(HabitId,LogDate) VALUES(?,?)").use { ps -> ps.setInt(1,habitId); ps.setString(2,today); ps.executeUpdate() }
                // Update streak
                val cal = Calendar.getInstance(); cal.add(Calendar.DATE,-1)
                val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.time)
                val hadYesterday = conn.prepareStatement("SELECT COUNT(*) FROM HabitLogs WHERE HabitId=? AND LogDate=?").use { ps -> ps.setInt(1,habitId); ps.setString(2,yesterday); val rs=ps.executeQuery(); rs.next(); rs.getInt(1) > 0 }
                if (hadYesterday) {
                    conn.prepareStatement("UPDATE Habits SET CurrentStreak=CurrentStreak+1,TotalDays=TotalDays+1 WHERE Id=?").use { ps -> ps.setInt(1,habitId); ps.executeUpdate() }
                } else {
                    conn.prepareStatement("UPDATE Habits SET CurrentStreak=1,TotalDays=TotalDays+1 WHERE Id=?").use { ps -> ps.setInt(1,habitId); ps.executeUpdate() }
                }
                true
            }
        } catch (e: Exception) { false }
    }

    fun getHabitLogs(habitId: Int, days: Int = 91): List<String> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT CAST(LogDate AS NVARCHAR) FROM HabitLogs WHERE HabitId=? AND LogDate >= DATEADD(day,-?,GETDATE()) AND Completed=1").use { ps ->
                    ps.setInt(1,habitId); ps.setInt(2,days); val rs=ps.executeQuery(); val list=mutableListOf<String>()
                    while(rs.next()) list.add(rs.getString(1))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun deleteHabit(habitId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM Habits WHERE Id=?").use { ps -> ps.setInt(1,habitId); ps.executeUpdate() > 0 }
            }
        } catch (e: Exception) { false }
    }

    // ========== DAILY CHALLENGES ==========
    fun getTodayChallenges(userId: Int): List<DailyChallenge> {
        return try {
            getConnection().use { conn ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                var challenges = conn.prepareStatement("SELECT Id,UserId,ChallengeType,Description,TargetValue,CurrentValue,IsCompleted,CAST(ChallengeDate AS NVARCHAR) FROM DailyChallenges WHERE UserId=? AND ChallengeDate=?").use { ps ->
                    ps.setInt(1,userId); ps.setString(2,today); val rs=ps.executeQuery(); val list=mutableListOf<DailyChallenge>()
                    while(rs.next()) list.add(DailyChallenge(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getBoolean(7),rs.getString(8)))
                    list
                }
                if (challenges.isEmpty()) {
                    generateDailyChallenges(userId, today, conn)
                    challenges = conn.prepareStatement("SELECT Id,UserId,ChallengeType,Description,TargetValue,CurrentValue,IsCompleted,CAST(ChallengeDate AS NVARCHAR) FROM DailyChallenges WHERE UserId=? AND ChallengeDate=?").use { ps ->
                        ps.setInt(1,userId); ps.setString(2,today); val rs=ps.executeQuery(); val list=mutableListOf<DailyChallenge>()
                        while(rs.next()) list.add(DailyChallenge(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getBoolean(7),rs.getString(8)))
                        list
                    }
                }
                challenges
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun generateDailyChallenges(userId: Int, date: String, conn: Connection) {
        val allTypes = listOf(
            Triple("complete_tasks", "Выполни %d задачи сегодня", listOf(2,3,5)),
            Triple("complete_before_deadline", "Выполни %d задачи до дедлайна", listOf(1,2)),
            Triple("use_categories", "Задачи из %d разных папок", listOf(2)),
            Triple("complete_high_priority", "Выполни %d задачи с высоким приоритетом", listOf(1,2)),
            Triple("complete_streak", "Поддержи серию %d+ дней", listOf(1))
        )
        val selected = allTypes.shuffled().take(3)
        selected.forEach { (type, desc, vals) ->
            val target = vals.random()
            conn.prepareStatement("INSERT INTO DailyChallenges(UserId,ChallengeType,Description,TargetValue,ChallengeDate) VALUES(?,?,?,?,?)").use { ps ->
                ps.setInt(1,userId); ps.setString(2,type); ps.setString(3,desc.format(target))
                ps.setInt(4,target); ps.setString(5,date); ps.executeUpdate()
            }
        }
    }

    private fun updateChallengeProgress(conn: Connection, userId: Int, type: String, increment: Int) {
        try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
            conn.prepareStatement("UPDATE DailyChallenges SET CurrentValue=CurrentValue+?, IsCompleted=CASE WHEN CurrentValue+? >= TargetValue THEN 1 ELSE IsCompleted END WHERE UserId=? AND ChallengeType=? AND ChallengeDate=? AND IsCompleted=0").use { ps ->
                ps.setInt(1,increment); ps.setInt(2,increment); ps.setInt(3,userId)
                ps.setString(4,type); ps.setString(5,today); ps.executeUpdate()
            }
        } catch (e: Exception) { }
    }

    // ========== AVATAR ==========
    fun updateAvatar(userId: Int, avatarId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Users SET AvatarId=? WHERE Id=?").use { ps -> ps.setInt(1,avatarId); ps.setInt(2,userId); ps.executeUpdate() > 0 }
            }
        } catch (e: Exception) { false }
    }

    // ========== PROFILE ==========
    fun getUserProfile(userId: Int): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT Username,Email,Role,TaskStreak,AvatarId,Xp,Coins,UserLevel FROM Users WHERE Id=?").use { ps ->
                    ps.setInt(1,userId); val rs=ps.executeQuery()
                    if(rs.next()) { result["username"]=rs.getString(1); result["email"]=rs.getString(2); result["role"]=rs.getString(3); result["streak"]=rs.getInt(4); result["avatarId"]=rs.getInt(5); result["xp"]=rs.getInt(6); result["coins"]=rs.getInt(7); result["level"]=rs.getInt(8) }
                }
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='completed'").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) result["tasksCompleted"]=rs.getInt(1) }
                conn.prepareStatement("SELECT COUNT(*) FROM Tasks WHERE UserId=? AND Status='pending'").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) result["tasksActive"]=rs.getInt(1) }
                conn.prepareStatement("SELECT COUNT(*) FROM Categories WHERE UserId=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) result["categories"]=rs.getInt(1) }
                conn.prepareStatement("SELECT COUNT(*) FROM UserAchievements WHERE UserId=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) result["achievements"]=rs.getInt(1) }
                conn.prepareStatement("SELECT COUNT(*) FROM MoodLogs WHERE UserId=?").use { ps -> ps.setInt(1,userId); val rs=ps.executeQuery(); if(rs.next()) result["moodLogs"]=rs.getInt(1) }
            }
        } catch (e: Exception) { Log.e("DB","getUserProfile: ${e.message}") }
        return result
    }

    // ========== ADMIN ==========
    fun getAllTasks(): List<Task> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT t.Id,t.UserId,t.Title,t.Description,t.Status,t.Priority,t.DueDate,t.CategoryId,u.Username,t.CompletedAt FROM Tasks t JOIN Users u ON t.UserId=u.Id WHERE t.Status!='trash' ORDER BY t.Status,t.Priority DESC").use { ps ->
                    val rs=ps.executeQuery(); val list=mutableListOf<Task>()
                    while(rs.next()) list.add(Task(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)?:"",rs.getString(5),rs.getInt(6),rs.getString(7),rs.getObject(8) as? Int,rs.getString(9),rs.getString(10)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun getAdminStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        try {
            getConnection().use { conn ->
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM Users").also { if(it.next()) stats["users"]=it.getInt(1) }
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM Tasks WHERE Status!='trash'").also { if(it.next()) stats["tasks"]=it.getInt(1) }
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM Tasks WHERE Status='completed'").also { if(it.next()) stats["completed"]=it.getInt(1) }
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM MoodLogs").also { if(it.next()) stats["moods"]=it.getInt(1) }
            }
        } catch (e: Exception) { }
        return stats
    }

    fun getLeaderboard(): List<Pair<String, Int>> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("SELECT TOP 5 u.Username, COUNT(t.Id) as cnt FROM Users u LEFT JOIN Tasks t ON u.Id=t.UserId AND t.Status='completed' GROUP BY u.Username ORDER BY cnt DESC").use { ps ->
                    val rs=ps.executeQuery(); val list=mutableListOf<Pair<String,Int>>()
                    while(rs.next()) list.add(Pair(rs.getString(1),rs.getInt(2)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    // ========== ADMIN: EXTENDED ==========
    data class AdminUser(val id: Int, val username: String, val email: String, val role: String,
                         val streak: Int, val totalTasks: Int, val completedTasks: Int)

    fun getAllUsers(): List<AdminUser> {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("""
                    SELECT u.Id, u.Username, u.Email, u.Role, u.TaskStreak,
                      (SELECT COUNT(*) FROM Tasks t WHERE t.UserId=u.Id AND t.Status!='trash') AS total,
                      (SELECT COUNT(*) FROM Tasks t WHERE t.UserId=u.Id AND t.Status='completed') AS done
                    FROM Users u ORDER BY done DESC
                """.trimIndent()).use { ps ->
                    val rs = ps.executeQuery(); val list = mutableListOf<AdminUser>()
                    while (rs.next()) list.add(AdminUser(
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7)))
                    list
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    // Extended system statistics for admin dashboard
    fun getAdminStatsExtended(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        try {
            getConnection().use { conn ->
                fun q(sql: String, key: String) {
                    conn.createStatement().executeQuery(sql).also { if (it.next()) stats[key] = it.getInt(1) }
                }
                q("SELECT COUNT(*) FROM Users", "users")
                q("SELECT COUNT(*) FROM Users WHERE Role='admin'", "admins")
                q("SELECT COUNT(*) FROM Tasks WHERE Status!='trash'", "tasks")
                q("SELECT COUNT(*) FROM Tasks WHERE Status='completed'", "completed")
                q("SELECT COUNT(*) FROM Tasks WHERE Status='pending'", "pending")
                q("SELECT COUNT(*) FROM Tasks WHERE Status='trash'", "trash")
                q("SELECT COUNT(*) FROM Categories", "categories")
                q("SELECT COUNT(*) FROM Habits", "habits")
                q("SELECT COUNT(*) FROM MoodLogs", "moods")
                q("SELECT COUNT(*) FROM UserAchievements", "achievements")
                q("SELECT ISNULL(MAX(TaskStreak),0) FROM Users", "maxStreak")
                q("SELECT COUNT(*) FROM Tasks WHERE Status='completed' AND CAST(CompletedAt AS DATE)=CAST(GETDATE() AS DATE)", "completedToday")
            }
        } catch (e: Exception) { Log.e("DB", "getAdminStatsExtended: ${e.message}") }
        return stats
    }

    // Promote / demote a user's role
    fun setUserRole(userId: Int, role: String): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE Users SET Role=? WHERE Id=?").use { ps ->
                    ps.setString(1, role); ps.setInt(2, userId); ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { false }
    }

    // Delete a user and all their data (cascade)
    fun deleteUser(userId: Int): Boolean {
        return try {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM Users WHERE Id=? AND Role!='admin'").use { ps ->
                    ps.setInt(1, userId); ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { false }
    }

    // ========== DEMO ACCOUNT (for screenshots) ==========
    fun seedDemoAccount() {
        try {
            getConnection().use { conn ->
                // Skip if demo already exists
                val exists = conn.prepareStatement("SELECT Id FROM Users WHERE Email='demo@todogame.com'").use { ps ->
                    val rs = ps.executeQuery(); if (rs.next()) rs.getInt(1) else 0
                }
                val userId: Int
                if (exists == 0) {
                    userId = conn.prepareStatement(
                        "INSERT INTO Users(Email,Username,PasswordHash,Role,TaskStreak,LastTaskDate,AvatarId) " +
                        "VALUES(?,?,?,?,?,CAST(GETDATE() AS DATE),?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                        ps.setString(1, "demo@todogame.com")
                        ps.setString(2, "Тимофей")
                        ps.setString(3, sha256("demo123"))
                        ps.setString(4, "user")
                        ps.setInt(5, 12)   // streak 12 days
                        ps.setInt(6, 6)    // dragon avatar
                        ps.executeUpdate()
                        val keys = ps.generatedKeys; keys.next(); keys.getInt(1)
                    }
                } else {
                    userId = exists
                    // already seeded — don't duplicate
                    return
                }

                // --- Categories ---
                val catIds = mutableListOf<Int>()
                listOf("Работа" to "#FF6B6B", "Учёба" to "#45B7D1", "Дом" to "#4ECDC4", "Здоровье" to "#82E0AA").forEach { (name, color) ->
                    conn.prepareStatement("INSERT INTO Categories(UserId,Name,Color) VALUES(?,?,?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                        ps.setInt(1, userId); ps.setString(2, name); ps.setString(3, color); ps.executeUpdate()
                        val k = ps.generatedKeys; k.next(); catIds.add(k.getInt(1))
                    }
                }

                // --- Active tasks ---
                data class T(val title: String, val desc: String, val prio: Int, val cat: Int, val days: Int)
                val activeTasks = listOf(
                    T("Подготовить отчёт по диплому", "Дописать главу 3 и приложение", 3, 1, 1),
                    T("Купить продукты", "Молоко, хлеб, овощи", 1, 2, 0),
                    T("Сделать зарядку", "20 минут утром", 2, 3, 0),
                    T("Прочитать главу учебника", "Базы данных, нормализация", 2, 1, 2),
                    T("Позвонить родителям", "", 1, 2, 1),
                    T("Тренировка в зале", "Ноги + спина", 3, 3, 0),
                    T("Оплатить интернет", "До 15 числа", 2, 2, 3),
                    T("Подготовиться к защите", "Прорепетировать речь", 3, 0, 5)
                )
                activeTasks.forEach { t ->
                    conn.prepareStatement(
                        "INSERT INTO Tasks(UserId,CategoryId,Title,Description,Status,Priority,DueDate) " +
                        "VALUES(?,?,?,?,'pending',?,DATEADD(DAY,?,CAST(GETDATE() AS DATE)))").use { ps ->
                        ps.setInt(1, userId)
                        if (t.cat == 0) ps.setNull(2, java.sql.Types.INTEGER) else ps.setInt(2, catIds[t.cat - 1])
                        ps.setString(3, t.title); ps.setString(4, t.desc)
                        ps.setInt(5, t.prio); ps.setInt(6, t.days); ps.executeUpdate()
                    }
                }

                // --- Completed tasks (spread over past days for streak/stats) ---
                val completedTitles = listOf(
                    "Утренняя пробежка", "Ответить на письма", "Помыть посуду",
                    "Написать конспект", "Сходить в магазин", "Сделать домашку",
                    "Убраться в комнате", "Изучить Kotlin", "Созвон с командой",
                    "Решить задачи по алгебре", "Полить цветы", "Зарядка",
                    "Прочитать статью", "Код-ревью", "Планёрка",
                    "Выгулять собаку", "Приготовить ужин", "Медитация",
                    "Английский — урок", "Бэклог задач"
                )
                completedTitles.forEachIndexed { i, title ->
                    val daysAgo = i % 12
                    conn.prepareStatement(
                        "INSERT INTO Tasks(UserId,CategoryId,Title,Status,Priority,CompletedAt) " +
                        "VALUES(?,?,?,'completed',?,DATEADD(DAY,?,GETDATE()))").use { ps ->
                        ps.setInt(1, userId)
                        ps.setInt(2, catIds[i % catIds.size])
                        ps.setString(3, title)
                        ps.setInt(4, (i % 3) + 1)
                        ps.setInt(5, -daysAgo)
                        ps.executeUpdate()
                    }
                }

                // --- Habits with heatmaps ---
                val habitDefs = listOf(
                    Triple("Зарядка", "#FF6B6B", 0.85),
                    Triple("Чтение", "#45B7D1", 0.7),
                    Triple("Вода 2л", "#4ECDC4", 0.9),
                    Triple("Без соцсетей", "#DDA0DD", 0.55)
                )
                habitDefs.forEach { (name, color, density) ->
                    val habitId = conn.prepareStatement(
                        "INSERT INTO Habits(UserId,Name,Color,CurrentStreak,TotalDays) VALUES(?,?,?,?,?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                        ps.setInt(1, userId); ps.setString(2, name); ps.setString(3, color)
                        ps.setInt(4, (density * 10).toInt()); ps.setInt(5, (density * 70).toInt())
                        ps.executeUpdate()
                        val k = ps.generatedKeys; k.next(); k.getInt(1)
                    }
                    // Fill heatmap for past 70 days based on density
                    for (d in 0 until 70) {
                        if (Math.random() < density) {
                            conn.prepareStatement(
                                "INSERT INTO HabitLogs(HabitId,LogDate,Completed) " +
                                "VALUES(?,DATEADD(DAY,?,CAST(GETDATE() AS DATE)),1)").use { ps ->
                                ps.setInt(1, habitId); ps.setInt(2, -d); ps.executeUpdate()
                            }
                        }
                    }
                }

                // --- Mood logs (past 25 days) ---
                val moods = listOf(4,5,3,4,5,4,3,5,4,4,3,4,5,5,4,3,4,4,5,4,3,5,4,4,5)
                val notes = listOf("Продуктивный день","Отлично!","Устал","Норм","Супер настроение")
                moods.forEachIndexed { i, m ->
                    conn.prepareStatement(
                        "INSERT INTO MoodLogs(UserId,Mood,Note,LogDate,TasksCompleted) " +
                        "VALUES(?,?,?,DATEADD(DAY,?,CAST(GETDATE() AS DATE)),?)").use { ps ->
                        ps.setInt(1, userId); ps.setInt(2, m)
                        ps.setString(3, notes[i % notes.size])
                        ps.setInt(4, -(moods.size - 1 - i))
                        ps.setInt(5, (1..5).random())
                        ps.executeUpdate()
                    }
                }

                // --- Grant some achievements ---
                conn.prepareStatement(
                    "INSERT INTO UserAchievements(UserId,AchievementId,UnlockedAt) " +
                    "SELECT ?, Id, DATEADD(DAY,-Id,GETDATE()) FROM Achievements " +
                    "WHERE AchievementType IN ('tasks_total','streak','categories') AND RequiredValue<=20").use { ps ->
                    ps.setInt(1, userId); ps.executeUpdate()
                }

                Log.i("DB", "Demo account seeded: demo@todogame.com / demo123")
            }
        } catch (e: Exception) { Log.e("DB", "seedDemoAccount: ${e.message}") }
    }
}
