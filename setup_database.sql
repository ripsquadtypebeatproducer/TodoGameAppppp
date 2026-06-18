-- TodoGame Database Setup v2.0
-- Run in SSMS connected to your SQL Server

CREATE DATABASE TodoGameDB;
GO
USE TodoGameDB;
GO

-- Users
CREATE TABLE Users (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    Email NVARCHAR(100) UNIQUE NOT NULL,
    Username NVARCHAR(50) NOT NULL,
    PasswordHash NVARCHAR(255) NOT NULL,
    Role NVARCHAR(20) DEFAULT 'user',
    TaskStreak INT DEFAULT 0,
    LastTaskDate DATE NULL,
    AvatarId INT DEFAULT 0
);

-- Categories
CREATE TABLE Categories (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL REFERENCES Users(Id) ON DELETE CASCADE,
    Name NVARCHAR(100) NOT NULL,
    Color NVARCHAR(20) DEFAULT '#7C4DFF'
);

-- Tasks
CREATE TABLE Tasks (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL REFERENCES Users(Id) ON DELETE CASCADE,
    CategoryId INT NULL REFERENCES Categories(Id) ON DELETE SET NULL,
    Title NVARCHAR(200) NOT NULL,
    Description NVARCHAR(MAX) NULL,
    Status NVARCHAR(20) DEFAULT 'pending',
    Priority INT DEFAULT 2,
    DueDate DATE NULL,
    CompletedAt DATETIME NULL,
    DeletedAt DATETIME NULL
);

-- Achievements
CREATE TABLE Achievements (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    Name NVARCHAR(100) NOT NULL,
    Description NVARCHAR(300) NOT NULL,
    RequiredValue INT NOT NULL,
    AchievementType NVARCHAR(50) NOT NULL,
    IconName NVARCHAR(50) NOT NULL,
    XpReward INT DEFAULT 0
);

-- UserAchievements
CREATE TABLE UserAchievements (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL REFERENCES Users(Id) ON DELETE CASCADE,
    AchievementId INT NOT NULL REFERENCES Achievements(Id) ON DELETE CASCADE,
    UnlockedAt DATETIME DEFAULT GETDATE()
);

-- MoodLogs (NEW)
CREATE TABLE MoodLogs (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL REFERENCES Users(Id) ON DELETE CASCADE,
    Mood INT NOT NULL CHECK (Mood BETWEEN 1 AND 5),
    Note NVARCHAR(500) DEFAULT '',
    LogDate DATE NOT NULL DEFAULT CAST(GETDATE() AS DATE),
    TasksCompleted INT DEFAULT 0
);

-- Habits (NEW)
CREATE TABLE Habits (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL REFERENCES Users(Id) ON DELETE CASCADE,
    Name NVARCHAR(100) NOT NULL,
    Color NVARCHAR(20) DEFAULT '#7C4DFF',
    CurrentStreak INT DEFAULT 0,
    TotalDays INT DEFAULT 0
);

-- HabitLogs (NEW)
CREATE TABLE HabitLogs (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    HabitId INT NOT NULL REFERENCES Habits(Id) ON DELETE CASCADE,
    LogDate DATE NOT NULL,
    Completed BIT DEFAULT 1
);

-- DailyChallenges (NEW)
CREATE TABLE DailyChallenges (
    Id INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL REFERENCES Users(Id) ON DELETE CASCADE,
    ChallengeType NVARCHAR(50) NOT NULL,
    Description NVARCHAR(200) NOT NULL,
    TargetValue INT DEFAULT 1,
    CurrentValue INT DEFAULT 0,
    IsCompleted BIT DEFAULT 0,
    ChallengeDate DATE DEFAULT CAST(GETDATE() AS DATE)
);

-- Admin user (password: admin123)
INSERT INTO Users (Email, Username, PasswordHash, Role)
VALUES ('admin@todogame.com', 'Admin',
        '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'admin');

-- Achievements seed
INSERT INTO Achievements (Name, Description, RequiredValue, AchievementType, IconName, XpReward) VALUES
('Первый шаг', 'Выполни первую задачу', 1, 'tasks_total', 'tasks_total', 5),
('Десятка', 'Выполни 10 задач', 10, 'tasks_total', 'tasks_total', 50),
('Полсотни', 'Выполни 50 задач', 50, 'tasks_total', 'tasks_total', 250),
('Продуктивный день', '5 задач за один день', 5, 'tasks_today', 'tasks_today', 25),
('Машина продуктивности', '10 задач за один день', 10, 'tasks_today', 'tasks_today', 50),
('3 дня подряд', 'Серия 3 дня', 3, 'streak', 'streak', 15),
('Неделя', 'Серия 7 дней', 7, 'streak', 'streak', 35),
('Месяц', 'Серия 30 дней', 30, 'streak', 'streak', 150),
('Организатор', 'Создай 3 папки', 3, 'categories', 'categories', 15),
('Пунктуальный', '10 задач до дедлайна', 10, 'before_deadline', 'before_deadline', 50),
('Без просрочек', '7 дней без просроченных', 7, 'no_overdue', 'no_overdue', 35),
('Настроение ОК', 'Заполни трекер 7 дней подряд', 7, 'mood_streak', 'mood_streak', 35),
('Привычка', 'Привычка 14 дней подряд', 14, 'habit_streak', 'habit_streak', 70),
('Вызов принят', 'Выполни 5 ежедневных вызовов', 5, 'challenges', 'challenges', 25),
('Легенда', 'Выполни 100 задач', 100, 'tasks_total', 'tasks_total', 500);

PRINT 'TodoGameDB v2.0 created successfully!';
