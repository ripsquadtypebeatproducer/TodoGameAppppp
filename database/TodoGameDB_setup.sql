-- ============================================================
--  TodoGame — скрипт создания базы данных (Microsoft SQL Server)
--  Запускать в SSMS под учётной записью с правами sysadmin.
--  Скрипт идемпотентный: можно запускать повторно без ошибок.
-- ============================================================

-- 1. Создание базы данных
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'TodoGameDB')
BEGIN
    CREATE DATABASE TodoGameDB;
END
GO

USE TodoGameDB;
GO

-- 2. Логин и пользователь для приложения (jTDS подключается под ним)
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'todogame')
BEGIN
    CREATE LOGIN todogame WITH PASSWORD = 'TodoGame123!',
        CHECK_POLICY = OFF, DEFAULT_DATABASE = TodoGameDB;
END
GO
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'todogame')
BEGIN
    CREATE USER todogame FOR LOGIN todogame;
    ALTER ROLE db_owner ADD MEMBER todogame;
END
GO

-- ============================================================
--  3. ТАБЛИЦЫ
-- ============================================================

-- Пользователи (с полями геймификации и питомца)
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Users' AND xtype='U')
CREATE TABLE Users (
    Id INT IDENTITY PRIMARY KEY,
    Email NVARCHAR(100) UNIQUE NOT NULL,
    Username NVARCHAR(50) NOT NULL,
    PasswordHash NVARCHAR(255) NOT NULL,
    Role NVARCHAR(20) DEFAULT 'user',
    TaskStreak INT DEFAULT 0,
    LastTaskDate DATE NULL,
    AvatarId INT DEFAULT 0,
    Xp INT DEFAULT 0,
    Coins INT DEFAULT 0,
    UserLevel INT DEFAULT 1,
    PetType NVARCHAR(20) DEFAULT 'fox',
    PetName NVARCHAR(50) DEFAULT N'Лис',
    PetHappiness INT DEFAULT 80,
    Theme NVARCHAR(20) DEFAULT 'light'
);
GO

-- Категории
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Categories' AND xtype='U')
CREATE TABLE Categories (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    Name NVARCHAR(100) NOT NULL,
    Color NVARCHAR(20) DEFAULT '#7C4DFF',
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE
);
GO

-- Задачи
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Tasks' AND xtype='U')
CREATE TABLE Tasks (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    CategoryId INT NULL,
    Title NVARCHAR(200) NOT NULL,
    Description NVARCHAR(MAX) NULL,
    Status NVARCHAR(20) DEFAULT 'pending',
    Priority INT DEFAULT 2,
    DueDate DATE NULL,
    CompletedAt DATETIME NULL,
    DeletedAt DATETIME NULL,
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    FOREIGN KEY (CategoryId) REFERENCES Categories(Id) ON DELETE SET NULL
);
GO

-- Достижения (каталог)
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Achievements' AND xtype='U')
CREATE TABLE Achievements (
    Id INT IDENTITY PRIMARY KEY,
    Name NVARCHAR(100) NOT NULL,
    Description NVARCHAR(300) NOT NULL,
    RequiredValue INT NOT NULL,
    AchievementType NVARCHAR(50) NOT NULL,
    IconName NVARCHAR(50) NOT NULL,
    XpReward INT DEFAULT 0
);
GO

-- Полученные достижения
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='UserAchievements' AND xtype='U')
CREATE TABLE UserAchievements (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    AchievementId INT NOT NULL,
    UnlockedAt DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    FOREIGN KEY (AchievementId) REFERENCES Achievements(Id) ON DELETE CASCADE
);
GO

-- Журнал настроения
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='MoodLogs' AND xtype='U')
CREATE TABLE MoodLogs (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    Mood INT NOT NULL CHECK (Mood BETWEEN 1 AND 5),
    Note NVARCHAR(500) DEFAULT '',
    LogDate DATE NOT NULL DEFAULT CAST(GETDATE() AS DATE),
    TasksCompleted INT DEFAULT 0,
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE
);
GO

-- Привычки
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Habits' AND xtype='U')
CREATE TABLE Habits (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    Name NVARCHAR(100) NOT NULL,
    Color NVARCHAR(20) DEFAULT '#7C4DFF',
    CurrentStreak INT DEFAULT 0,
    TotalDays INT DEFAULT 0,
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE
);
GO

-- Журнал привычек
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='HabitLogs' AND xtype='U')
CREATE TABLE HabitLogs (
    Id INT IDENTITY PRIMARY KEY,
    HabitId INT NOT NULL,
    LogDate DATE NOT NULL,
    Completed BIT DEFAULT 1,
    FOREIGN KEY (HabitId) REFERENCES Habits(Id) ON DELETE CASCADE
);
GO

-- Дневные вызовы
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='DailyChallenges' AND xtype='U')
CREATE TABLE DailyChallenges (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    ChallengeType NVARCHAR(50) NOT NULL,
    Description NVARCHAR(200) NOT NULL,
    TargetValue INT DEFAULT 1,
    CurrentValue INT DEFAULT 0,
    IsCompleted BIT DEFAULT 0,
    ChallengeDate DATE DEFAULT CAST(GETDATE() AS DATE),
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE
);
GO

-- ============================================================
--  НОВЫЕ ТАБЛИЦЫ (друзья, совместные задачи, магазин)
-- ============================================================

-- Дружеские связи (заявки + принятые)
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Friendships' AND xtype='U')
CREATE TABLE Friendships (
    Id INT IDENTITY PRIMARY KEY,
    RequesterId INT NOT NULL,
    AddresseeId INT NOT NULL,
    Status NVARCHAR(20) DEFAULT 'pending',   -- pending / accepted
    CreatedAt DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (RequesterId) REFERENCES Users(Id),
    FOREIGN KEY (AddresseeId) REFERENCES Users(Id)
);
GO

-- Совместные (расшаренные) задачи
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='TaskShares' AND xtype='U')
CREATE TABLE TaskShares (
    Id INT IDENTITY PRIMARY KEY,
    TaskId INT NOT NULL,
    SharedWithUserId INT NOT NULL,
    CreatedAt DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (TaskId) REFERENCES Tasks(Id) ON DELETE CASCADE,
    FOREIGN KEY (SharedWithUserId) REFERENCES Users(Id)
);
GO

-- Каталог товаров магазина
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='ShopItems' AND xtype='U')
CREATE TABLE ShopItems (
    Id INT IDENTITY PRIMARY KEY,
    ItemType NVARCHAR(20) NOT NULL,          -- pet / theme / badge / avatar
    Name NVARCHAR(100) NOT NULL,
    IconValue NVARCHAR(50) NOT NULL,
    Price INT NOT NULL DEFAULT 0
);
GO

-- Купленные товары
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='UserItems' AND xtype='U')
CREATE TABLE UserItems (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL,
    ItemId INT NOT NULL,
    PurchasedAt DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    FOREIGN KEY (ItemId) REFERENCES ShopItems(Id)
);
GO

-- ============================================================
--  4. МИГРАЦИИ (если таблица Users уже существовала без новых колонок)
-- ============================================================
IF COL_LENGTH('Users','Xp') IS NULL ALTER TABLE Users ADD Xp INT DEFAULT 0;
IF COL_LENGTH('Users','Coins') IS NULL ALTER TABLE Users ADD Coins INT DEFAULT 0;
IF COL_LENGTH('Users','UserLevel') IS NULL ALTER TABLE Users ADD UserLevel INT DEFAULT 1;
IF COL_LENGTH('Users','PetType') IS NULL ALTER TABLE Users ADD PetType NVARCHAR(20) DEFAULT 'fox';
IF COL_LENGTH('Users','PetName') IS NULL ALTER TABLE Users ADD PetName NVARCHAR(50) DEFAULT N'Лис';
IF COL_LENGTH('Users','PetHappiness') IS NULL ALTER TABLE Users ADD PetHappiness INT DEFAULT 80;
IF COL_LENGTH('Users','Theme') IS NULL ALTER TABLE Users ADD Theme NVARCHAR(20) DEFAULT 'light';
GO

-- ============================================================
--  5. НАПОЛНЕНИЕ ДАННЫМИ
-- ============================================================

-- Администратор (пароль admin123, хеш SHA-256)
IF NOT EXISTS (SELECT * FROM Users WHERE Email = 'admin@todogame.com')
BEGIN
    INSERT INTO Users (Email, Username, PasswordHash, Role)
    VALUES ('admin@todogame.com', 'Admin',
        '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'admin');
END
GO

-- Достижения
IF NOT EXISTS (SELECT * FROM Achievements)
BEGIN
    INSERT INTO Achievements (Name, Description, RequiredValue, AchievementType, IconName, XpReward) VALUES
    (N'Первый шаг',      N'Выполни первую задачу',     1,  'tasks',  'star',   10),
    (N'Десятка',         N'Выполни 10 задач',          10, 'tasks',  'medal',  25),
    (N'Полсотни',        N'Выполни 50 задач',          50, 'tasks',  'trophy', 50),
    (N'Продуктивный день', N'5 задач за один день',     5,  'daily',  'fire',   20),
    (N'3 дня подряд',    N'Серия из 3 дней',           3,  'streak', 'fire',   15),
    (N'Неделя',          N'Серия из 7 дней',           7,  'streak', 'fire',   30),
    (N'Месяц силы',      N'Серия из 30 дней',          30, 'streak', 'crown',  100);
END
GO

-- Товары магазина
IF NOT EXISTS (SELECT * FROM ShopItems)
BEGIN
    INSERT INTO ShopItems (ItemType, Name, IconValue, Price) VALUES
    ('pet',    N'Котёнок',      'cat',    150),
    ('pet',    N'Панда',        'panda',  150),
    ('pet',    N'Дракончик',    'dragon', 150),
    ('theme',  N'Тёмная тема',  'dark',   100),
    ('badge',  N'Новичок',      N'🌱',    80),
    ('badge',  N'Трудяга',      N'💪',    80),
    ('badge',  N'Легенда',      N'👑',    80),
    ('badge',  N'Звезда',       N'⭐',    80),
    ('avatar', N'Орёл',         N'🦅',    50),
    ('avatar', N'Единорог',     N'🦄',    50),
    ('avatar', N'Дракон',       N'🐲',    50),
    ('avatar', N'Молния',       N'⚡',    50);
END
GO

PRINT 'TodoGameDB готова к работе.';
GO
