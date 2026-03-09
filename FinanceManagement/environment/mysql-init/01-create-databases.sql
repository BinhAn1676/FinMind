-- Script tự động tạo tất cả databases khi MySQL container khởi động lần đầu
-- Chỉ chạy khi databases chưa tồn tại

-- Database cho UserService
CREATE DATABASE IF NOT EXISTS `user` 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- Database cho FinanceService
CREATE DATABASE IF NOT EXISTS `finance` 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- Database cho KeyManagementService
CREATE DATABASE IF NOT EXISTS `key` 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- Grant privileges (nếu cần tạo user riêng)
-- CREATE USER IF NOT EXISTS 'finance_user'@'%' IDENTIFIED BY 'password';
-- GRANT ALL PRIVILEGES ON `user`.* TO 'finance_user'@'%';
-- GRANT ALL PRIVILEGES ON `finance`.* TO 'finance_user'@'%';
-- GRANT ALL PRIVILEGES ON `key`.* TO 'finance_user'@'%';
-- FLUSH PRIVILEGES;
