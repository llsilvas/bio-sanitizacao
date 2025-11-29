-- Script de inicialização para criar os databases necessários
-- Executado automaticamente quando o container SQL Server é iniciado pela primeira vez

USE master;
GO

-- Criar database para metadados do batch
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'batch_metadata')
BEGIN
    CREATE DATABASE batch_metadata;
    PRINT 'Database batch_metadata criado com sucesso';
END
ELSE
BEGIN
    PRINT 'Database batch_metadata já existe';
END
GO

-- Criar database para dados de deduplicação
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'deduplicacao')
BEGIN
    CREATE DATABASE deduplicacao;
    PRINT 'Database deduplicacao criado com sucesso';
END
ELSE
BEGIN
    PRINT 'Database deduplicacao já existe';
END
GO

-- Configurações de performance para batch_metadata
USE batch_metadata;
GO

ALTER DATABASE batch_metadata SET RECOVERY SIMPLE;
ALTER DATABASE batch_metadata SET AUTO_CREATE_STATISTICS ON;
ALTER DATABASE batch_metadata SET AUTO_UPDATE_STATISTICS ON;
GO

-- Configurações de performance para deduplicacao
USE deduplicacao;
GO

ALTER DATABASE deduplicacao SET RECOVERY SIMPLE;
ALTER DATABASE deduplicacao SET AUTO_CREATE_STATISTICS ON;
ALTER DATABASE deduplicacao SET AUTO_UPDATE_STATISTICS ON;
GO

PRINT 'Databases configurados com sucesso';