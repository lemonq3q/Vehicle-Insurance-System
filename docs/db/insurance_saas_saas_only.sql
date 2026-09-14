-- Full schema/triggers plus SaaS-core data export from insurance_saas
-- ASCII-safe version: comments removed and text data encoded as UTF-8 hex literals.
-- Insurance business table structures are included; insurance business data is excluded.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `insurance_saas` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `insurance_saas`;
DROP TABLE IF EXISTS `auth_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `system_code` varchar(32) DEFAULT NULL,
  `type` varchar(20) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `route_path` varchar(200) DEFAULT NULL,
  `component` varchar(200) DEFAULT NULL,
  `sort_no` int DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_auth_permission_bi` BEFORE INSERT ON `auth_permission` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_auth_permission_bu` BEFORE UPDATE ON `auth_permission` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `auth_permission_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_permission_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `system_code` varchar(32) DEFAULT NULL,
  `type` varchar(20) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `route_path` varchar(200) DEFAULT NULL,
  `component` varchar(200) DEFAULT NULL,
  `sort_no` int DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `auth_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `role_scope` varchar(32) DEFAULT NULL,
  `builtin` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_auth_role_bi` BEFORE INSERT ON `auth_role` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_auth_role_bu` BEFORE UPDATE ON `auth_role` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `auth_role_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_role_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `role_scope` varchar(32) DEFAULT NULL,
  `builtin` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `auth_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint DEFAULT NULL,
  `permission_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `auth_role_permission_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_role_permission_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint DEFAULT NULL,
  `permission_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_insurance_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_insurance_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `options_json` json DEFAULT NULL,
  `default_option_json` varchar(100) DEFAULT NULL,
  `deductible_options_json` json DEFAULT NULL,
  `default_deductible_option_json` varchar(100) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_insurance_product_bi` BEFORE INSERT ON `biz_insurance_product` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_insurance_product_bu` BEFORE UPDATE ON `biz_insurance_product` FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_insurance_product_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_insurance_product_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `options_json` json DEFAULT NULL,
  `default_option_json` varchar(100) DEFAULT NULL,
  `deductible_options_json` json DEFAULT NULL,
  `default_deductible_option_json` varchar(100) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_insurance_product_bak_20260715_scalar`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_insurance_product_bak_20260715_scalar` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `options_json` json DEFAULT NULL,
  `default_option_json` json DEFAULT NULL,
  `deductible_options_json` json DEFAULT NULL,
  `default_deductible_option_json` json DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_merchant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `location` varchar(20) DEFAULT NULL,
  `address` varchar(100) DEFAULT NULL,
  `contact` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `bank` varchar(100) DEFAULT NULL,
  `bank_card_num` varchar(100) DEFAULT NULL,
  `channel` varchar(100) DEFAULT NULL,
  `service_phone` varchar(20) DEFAULT NULL,
  `default_area_code` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_merchant_enterprise_code` (`enterprise_id`,`code`),
  KEY `idx_biz_merchant_category` (`enterprise_id`,`category_id`,`deleted`),
  KEY `idx_biz_merchant_name` (`enterprise_id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=24400 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_bi` BEFORE INSERT ON `biz_merchant` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_bu` BEFORE UPDATE ON `biz_merchant` FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_merchant_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `location` varchar(20) DEFAULT NULL,
  `address` varchar(100) DEFAULT NULL,
  `contact` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `bank` varchar(100) DEFAULT NULL,
  `bank_card_num` varchar(100) DEFAULT NULL,
  `channel` varchar(100) DEFAULT NULL,
  `service_phone` varchar(20) DEFAULT NULL,
  `default_area_code` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_biz_merchant_archive_category` (`enterprise_id`,`category_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_merchant_area`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_area` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23183 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_area_bi` BEFORE INSERT ON `biz_merchant_area` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_area_bu` BEFORE UPDATE ON `biz_merchant_area` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_merchant_area_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_area_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_merchant_bak_20260715_code_unique`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_bak_20260715_code_unique` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `location` varchar(20) DEFAULT NULL,
  `address` varchar(100) DEFAULT NULL,
  `contact` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `bank` varchar(100) DEFAULT NULL,
  `bank_card_num` varchar(100) DEFAULT NULL,
  `channel` varchar(100) DEFAULT NULL,
  `service_phone` varchar(20) DEFAULT NULL,
  `default_area_code` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_merchant_bak_20260715_upstream_contact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_bak_20260715_upstream_contact` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `location` varchar(20) DEFAULT NULL,
  `address` varchar(100) DEFAULT NULL,
  `bank` varchar(100) DEFAULT NULL,
  `bank_card_num` varchar(100) DEFAULT NULL,
  `channel` varchar(100) DEFAULT NULL,
  `service_phone` varchar(20) DEFAULT NULL,
  `default_area_code` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_merchant_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `direction` varchar(20) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `sort_no` int NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_merchant_category_code` (`code`,`deleted`),
  KEY `idx_biz_merchant_category_direction` (`direction`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_category_bi` BEFORE INSERT ON `biz_merchant_category` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_category_bu` BEFORE UPDATE ON `biz_merchant_category` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_merchant_staff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_staff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `id_num` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_staff_list` (`enterprise_id`,`merchant_id`,`status`,`deleted`),
  KEY `idx_merchant_staff_phone` (`enterprise_id`,`phone`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1055 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_staff_bi` BEFORE INSERT ON `biz_merchant_staff` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_staff_bu` BEFORE UPDATE ON `biz_merchant_staff` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_merchant_staff_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_staff_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `id_num` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `archive_batch_no` varchar(64) DEFAULT NULL,
  `archived_at` datetime DEFAULT NULL,
  `archive_reason` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_merchant_staff_list` (`enterprise_id`,`merchant_id`,`status`,`deleted`),
  KEY `idx_merchant_staff_phone` (`enterprise_id`,`phone`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_merchant_staff_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_staff_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `staff_id` bigint DEFAULT NULL,
  `role_code` varchar(32) NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `contact_merchant_key` bigint GENERATED ALWAYS AS ((case when ((`role_code` = _utf8mb4'CONTACT') and (`deleted` = 0)) then `merchant_id` else NULL end)) STORED,
  `default_payee_merchant_key` bigint GENERATED ALWAYS AS ((case when ((`role_code` = _utf8mb4'PAYEE') and (`is_default` = 1) and (`deleted` = 0)) then `merchant_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_staff_role` (`enterprise_id`,`staff_id`,`role_code`,`deleted`),
  UNIQUE KEY `uk_merchant_contact` (`contact_merchant_key`),
  UNIQUE KEY `uk_merchant_default_payee` (`default_payee_merchant_key`),
  KEY `idx_merchant_staff_role_list` (`enterprise_id`,`merchant_id`,`role_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1066 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_staff_role_bi` BEFORE INSERT ON `biz_merchant_staff_role` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_merchant_staff_role_bu` BEFORE UPDATE ON `biz_merchant_staff_role` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_merchant_staff_role_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_merchant_staff_role_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `staff_id` bigint DEFAULT NULL,
  `role_code` varchar(32) NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `contact_merchant_key` bigint GENERATED ALWAYS AS ((case when ((`role_code` = _utf8mb4'CONTACT') and (`deleted` = 0)) then `merchant_id` else NULL end)) STORED,
  `default_payee_merchant_key` bigint GENERATED ALWAYS AS ((case when ((`role_code` = _utf8mb4'PAYEE') and (`is_default` = 1) and (`deleted` = 0)) then `merchant_id` else NULL end)) STORED,
  `archive_batch_no` varchar(64) DEFAULT NULL,
  `archived_at` datetime DEFAULT NULL,
  `archive_reason` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_merchant_staff_role_list` (`enterprise_id`,`merchant_id`,`role_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_ocr_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_ocr_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `file_id` bigint DEFAULT NULL,
  `ocr_type` varchar(50) DEFAULT NULL,
  `provider` varchar(50) DEFAULT NULL,
  `request_id` varchar(100) DEFAULT NULL,
  `success` tinyint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ocr_enterprise_created` (`enterprise_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_vehicle_certificate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_vehicle_certificate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `brand_model` varchar(100) DEFAULT NULL,
  `vehicle_type` varchar(100) DEFAULT NULL,
  `vehicle_code` varchar(100) DEFAULT NULL,
  `engine_code` varchar(100) DEFAULT NULL,
  `seats` int DEFAULT NULL,
  `curb_weight` int DEFAULT NULL,
  `displacement` int DEFAULT NULL,
  `approved_load_capacity` int DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_vehicle_certificate_bi` BEFORE INSERT ON `biz_vehicle_certificate` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_vehicle_certificate_bu` BEFORE UPDATE ON `biz_vehicle_certificate` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_vehicle_certificate_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_vehicle_certificate_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `brand_model` varchar(100) DEFAULT NULL,
  `vehicle_type` varchar(100) DEFAULT NULL,
  `vehicle_code` varchar(100) DEFAULT NULL,
  `engine_code` varchar(100) DEFAULT NULL,
  `seats` int DEFAULT NULL,
  `curb_weight` int DEFAULT NULL,
  `displacement` int DEFAULT NULL,
  `approved_load_capacity` int DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_vehicle_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_vehicle_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `invoice_amount` decimal(15,2) DEFAULT NULL,
  `buyer_name` varchar(100) DEFAULT NULL,
  `buyer_id_num` varchar(50) DEFAULT NULL,
  `vehicle_type` varchar(100) DEFAULT NULL,
  `brand_model` varchar(100) DEFAULT NULL,
  `vehicle_code` varchar(100) DEFAULT NULL,
  `engine_code` varchar(100) DEFAULT NULL,
  `seats` int DEFAULT NULL,
  `approved_load_capacity` int DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_vehicle_invoice_bi` BEFORE INSERT ON `biz_vehicle_invoice` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_vehicle_invoice_bu` BEFORE UPDATE ON `biz_vehicle_invoice` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_vehicle_invoice_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_vehicle_invoice_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `invoice_amount` decimal(15,2) DEFAULT NULL,
  `buyer_name` varchar(100) DEFAULT NULL,
  `buyer_id_num` varchar(50) DEFAULT NULL,
  `vehicle_type` varchar(100) DEFAULT NULL,
  `brand_model` varchar(100) DEFAULT NULL,
  `vehicle_code` varchar(100) DEFAULT NULL,
  `engine_code` varchar(100) DEFAULT NULL,
  `seats` int DEFAULT NULL,
  `approved_load_capacity` int DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_vehicle_license`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_vehicle_license` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `license_plate` varchar(50) DEFAULT NULL,
  `vehicle_type` varchar(100) DEFAULT NULL,
  `owner_name` varchar(100) DEFAULT NULL,
  `usage_nature` varchar(100) DEFAULT NULL,
  `brand_model` varchar(100) DEFAULT NULL,
  `vehicle_code` varchar(100) DEFAULT NULL,
  `engine_code` varchar(100) DEFAULT NULL,
  `registration_date` datetime DEFAULT NULL,
  `issue_date` datetime DEFAULT NULL,
  `seats` int DEFAULT NULL,
  `approved_load_capacity` int DEFAULT NULL,
  `curb_weight` int DEFAULT NULL,
  `is_transfer` varchar(20) DEFAULT NULL,
  `transfer_date` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_vehicle_license_bi` BEFORE INSERT ON `biz_vehicle_license` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_vehicle_license_bu` BEFORE UPDATE ON `biz_vehicle_license` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_vehicle_license_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_vehicle_license_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `license_plate` varchar(50) DEFAULT NULL,
  `vehicle_type` varchar(100) DEFAULT NULL,
  `owner_name` varchar(100) DEFAULT NULL,
  `usage_nature` varchar(100) DEFAULT NULL,
  `brand_model` varchar(100) DEFAULT NULL,
  `vehicle_code` varchar(100) DEFAULT NULL,
  `engine_code` varchar(100) DEFAULT NULL,
  `registration_date` datetime DEFAULT NULL,
  `issue_date` datetime DEFAULT NULL,
  `seats` int DEFAULT NULL,
  `approved_load_capacity` int DEFAULT NULL,
  `curb_weight` int DEFAULT NULL,
  `is_transfer` varchar(20) DEFAULT NULL,
  `transfer_date` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) NOT NULL,
  `type` tinyint DEFAULT '0',
  `owner_type` tinyint DEFAULT '0',
  `owner_name` varchar(100) DEFAULT NULL,
  `owner_phone` varchar(20) DEFAULT NULL,
  `owner_id_num` varchar(50) DEFAULT NULL,
  `organization_name` varchar(100) DEFAULT NULL,
  `social_credit_code` varchar(100) DEFAULT NULL,
  `create_merchant_id` bigint DEFAULT NULL,
  `source_staff_id` bigint DEFAULT NULL,
  `handle_merchant_id` bigint DEFAULT NULL,
  `insurance_merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `commercial_insurance_start_time` bigint DEFAULT NULL,
  `compulsory_insurance_start_time` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remind_status` tinyint DEFAULT '0',
  `renewal_status_cycle` int NOT NULL DEFAULT '0',
  `renewal_reminder_disabled` tinyint NOT NULL DEFAULT '0',
  `follow_up_res` varchar(1000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `handle_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_workorder_enterprise_code` (`enterprise_id`,`code`),
  KEY `idx_workorder_source_staff` (`enterprise_id`,`source_staff_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_bi` BEFORE INSERT ON `biz_workorder` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_bu` BEFORE UPDATE ON `biz_workorder` FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT '0',
  `owner_type` tinyint DEFAULT '0',
  `owner_name` varchar(100) DEFAULT NULL,
  `owner_phone` varchar(20) DEFAULT NULL,
  `owner_id_num` varchar(50) DEFAULT NULL,
  `organization_name` varchar(100) DEFAULT NULL,
  `social_credit_code` varchar(100) DEFAULT NULL,
  `create_merchant_id` bigint DEFAULT NULL,
  `source_staff_id` bigint DEFAULT NULL,
  `handle_merchant_id` bigint DEFAULT NULL,
  `insurance_merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `commercial_insurance_start_time` bigint DEFAULT NULL,
  `compulsory_insurance_start_time` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remind_status` tinyint DEFAULT '0',
  `renewal_status_cycle` int NOT NULL DEFAULT '0',
  `renewal_reminder_disabled` tinyint NOT NULL DEFAULT '0',
  `follow_up_res` varchar(1000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `handle_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_workorder_archive_source_staff` (`enterprise_id`,`source_staff_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_bak_20260715_code_unique`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_bak_20260715_code_unique` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT '0',
  `owner_type` tinyint DEFAULT '0',
  `owner_name` varchar(100) DEFAULT NULL,
  `owner_phone` varchar(20) DEFAULT NULL,
  `owner_id_num` varchar(50) DEFAULT NULL,
  `organization_name` varchar(100) DEFAULT NULL,
  `social_credit_code` varchar(100) DEFAULT NULL,
  `create_merchant_id` bigint DEFAULT NULL,
  `source_staff_id` bigint DEFAULT NULL,
  `handle_merchant_id` bigint DEFAULT NULL,
  `insurance_merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `commercial_insurance_start_time` bigint DEFAULT NULL,
  `compulsory_insurance_start_time` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remind_status` tinyint DEFAULT '0',
  `renewal_status_cycle` int NOT NULL DEFAULT '0',
  `renewal_reminder_disabled` tinyint NOT NULL DEFAULT '0',
  `follow_up_res` varchar(1000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `handle_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_bak_20260715_recurring_renewal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_bak_20260715_recurring_renewal` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT '0',
  `owner_type` tinyint DEFAULT '0',
  `owner_name` varchar(100) DEFAULT NULL,
  `owner_phone` varchar(20) DEFAULT NULL,
  `owner_id_num` varchar(50) DEFAULT NULL,
  `organization_name` varchar(100) DEFAULT NULL,
  `social_credit_code` varchar(100) DEFAULT NULL,
  `create_merchant_id` bigint DEFAULT NULL,
  `source_staff_id` bigint DEFAULT NULL,
  `handle_merchant_id` bigint DEFAULT NULL,
  `insurance_merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `commercial_insurance_start_time` bigint DEFAULT NULL,
  `compulsory_insurance_start_time` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remind_status` tinyint DEFAULT '0',
  `follow_up_res` varchar(1000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `handle_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_bak_20260715_start_time`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_bak_20260715_start_time` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT '0',
  `owner_type` tinyint DEFAULT '0',
  `owner_name` varchar(100) DEFAULT NULL,
  `owner_phone` varchar(20) DEFAULT NULL,
  `owner_id_num` varchar(50) DEFAULT NULL,
  `organization_name` varchar(100) DEFAULT NULL,
  `social_credit_code` varchar(100) DEFAULT NULL,
  `create_merchant_id` bigint DEFAULT NULL,
  `source_staff_id` bigint DEFAULT NULL,
  `handle_merchant_id` bigint DEFAULT NULL,
  `insurance_merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remind_status` tinyint DEFAULT '0',
  `follow_up_res` varchar(1000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `handle_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_bak_20260715_status_cycle`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_bak_20260715_status_cycle` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `type` tinyint DEFAULT '0',
  `owner_type` tinyint DEFAULT '0',
  `owner_name` varchar(100) DEFAULT NULL,
  `owner_phone` varchar(20) DEFAULT NULL,
  `owner_id_num` varchar(50) DEFAULT NULL,
  `organization_name` varchar(100) DEFAULT NULL,
  `social_credit_code` varchar(100) DEFAULT NULL,
  `create_merchant_id` bigint DEFAULT NULL,
  `source_staff_id` bigint DEFAULT NULL,
  `handle_merchant_id` bigint DEFAULT NULL,
  `insurance_merchant_id` bigint DEFAULT NULL,
  `area_code` varchar(20) DEFAULT NULL,
  `commercial_insurance_start_time` bigint DEFAULT NULL,
  `compulsory_insurance_start_time` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remind_status` tinyint DEFAULT '0',
  `renewal_reminder_disabled` tinyint NOT NULL DEFAULT '0',
  `follow_up_res` varchar(1000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `handle_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_commission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_commission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `side` varchar(20) DEFAULT NULL,
  `compute_type` tinyint DEFAULT '0',
  `commercial_percentage` decimal(8,2) DEFAULT NULL,
  `compulsory_percentage` decimal(8,2) DEFAULT NULL,
  `vehicle_tax_percentage` decimal(8,2) DEFAULT NULL,
  `non_motor_percentage` decimal(8,2) DEFAULT NULL,
  `commercial_amount` decimal(12,2) DEFAULT NULL,
  `compulsory_amount` decimal(12,2) DEFAULT NULL,
  `vehicle_tax_amount` decimal(12,2) DEFAULT NULL,
  `non_motor_amount` decimal(12,2) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=194 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_commission_bi` BEFORE INSERT ON `biz_workorder_commission` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_commission_bu` BEFORE UPDATE ON `biz_workorder_commission` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_commission_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_commission_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `side` varchar(20) DEFAULT NULL,
  `compute_type` tinyint DEFAULT '0',
  `commercial_percentage` decimal(8,2) DEFAULT NULL,
  `compulsory_percentage` decimal(8,2) DEFAULT NULL,
  `vehicle_tax_percentage` decimal(8,2) DEFAULT NULL,
  `non_motor_percentage` decimal(8,2) DEFAULT NULL,
  `commercial_amount` decimal(12,2) DEFAULT NULL,
  `compulsory_amount` decimal(12,2) DEFAULT NULL,
  `vehicle_tax_amount` decimal(12,2) DEFAULT NULL,
  `non_motor_amount` decimal(12,2) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `file_id` bigint DEFAULT NULL,
  `file_type` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=535 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_file_bi` BEFORE INSERT ON `biz_workorder_file` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_file_bu` BEFORE UPDATE ON `biz_workorder_file` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_file_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_file_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `file_id` bigint DEFAULT NULL,
  `file_type` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_insurance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_insurance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `insurance_id` bigint DEFAULT NULL,
  `option_json` varchar(100) DEFAULT NULL,
  `deductible_option_json` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=639 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_insurance_bi` BEFORE INSERT ON `biz_workorder_insurance` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_insurance_bu` BEFORE UPDATE ON `biz_workorder_insurance` FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_insurance_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_insurance_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `insurance_id` bigint DEFAULT NULL,
  `option_json` varchar(100) DEFAULT NULL,
  `deductible_option_json` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_insurance_bak_20260715_scalar`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_insurance_bak_20260715_scalar` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `insurance_id` bigint DEFAULT NULL,
  `option_json` json DEFAULT NULL,
  `deductible_option_json` json DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_insurance_bak_20260715_start_time`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_insurance_bak_20260715_start_time` (
  `id` bigint NOT NULL DEFAULT '0',
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `insurance_id` bigint DEFAULT NULL,
  `option_json` varchar(500) DEFAULT NULL,
  `deductible_option_json` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_logistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_logistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `tracking_num` varchar(100) DEFAULT NULL,
  `logistics_company` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_logistics_bi` BEFORE INSERT ON `biz_workorder_logistics` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_logistics_bu` BEFORE UPDATE ON `biz_workorder_logistics` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_logistics_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_logistics_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `tracking_num` varchar(100) DEFAULT NULL,
  `logistics_company` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `required_pay_amount` decimal(12,2) DEFAULT NULL,
  `payee_staff_id` bigint DEFAULT NULL,
  `payee_name` varchar(100) DEFAULT NULL,
  `payee_phone` varchar(20) DEFAULT NULL,
  `payee_id_num` varchar(100) DEFAULT NULL,
  `merchant_bank` varchar(100) DEFAULT NULL,
  `merchant_bank_card_num` varchar(100) DEFAULT NULL,
  `pay_remark` varchar(500) DEFAULT NULL,
  `pay_failed_remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_workorder_payment_workorder` (`enterprise_id`,`workorder_id`,`deleted`),
  KEY `idx_workorder_payment_payee` (`enterprise_id`,`payee_staff_id`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_payment_bi` BEFORE INSERT ON `biz_workorder_payment` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_payment_bu` BEFORE UPDATE ON `biz_workorder_payment` FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_payment_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_payment_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `required_pay_amount` decimal(12,2) DEFAULT NULL,
  `payee_staff_id` bigint DEFAULT NULL,
  `payee_name` varchar(100) DEFAULT NULL,
  `payee_phone` varchar(20) DEFAULT NULL,
  `payee_id_num` varchar(100) DEFAULT NULL,
  `merchant_bank` varchar(100) DEFAULT NULL,
  `merchant_bank_card_num` varchar(100) DEFAULT NULL,
  `pay_remark` varchar(500) DEFAULT NULL,
  `pay_failed_remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_workorder_payment_archive_workorder` (`enterprise_id`,`workorder_id`,`deleted`),
  KEY `idx_workorder_payment_archive_payee` (`enterprise_id`,`payee_staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_quote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_quote` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `quotation_no` varchar(100) DEFAULT NULL,
  `commercial_amount` decimal(12,2) DEFAULT NULL,
  `compulsory_amount` decimal(12,2) DEFAULT NULL,
  `vehicle_and_tax_amount` decimal(12,2) DEFAULT NULL,
  `non_motor_amount` decimal(12,2) DEFAULT NULL,
  `non_motor_insurance_name` varchar(100) DEFAULT NULL,
  `non_motor_coverage_amount` decimal(12,2) DEFAULT NULL,
  `quotation_remark` varchar(500) DEFAULT NULL,
  `quotation_failed_remark` varchar(500) DEFAULT NULL,
  `quotation_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_quote_bi` BEFORE INSERT ON `biz_workorder_quote` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_quote_bu` BEFORE UPDATE ON `biz_workorder_quote` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_quote_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_quote_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `quotation_no` varchar(100) DEFAULT NULL,
  `commercial_amount` decimal(12,2) DEFAULT NULL,
  `compulsory_amount` decimal(12,2) DEFAULT NULL,
  `vehicle_and_tax_amount` decimal(12,2) DEFAULT NULL,
  `non_motor_amount` decimal(12,2) DEFAULT NULL,
  `non_motor_insurance_name` varchar(100) DEFAULT NULL,
  `non_motor_coverage_amount` decimal(12,2) DEFAULT NULL,
  `quotation_remark` varchar(500) DEFAULT NULL,
  `quotation_failed_remark` varchar(500) DEFAULT NULL,
  `quotation_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_workorder_underwriting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_underwriting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `underwriting_remark` varchar(500) DEFAULT NULL,
  `underwriting_failed_remark` varchar(500) DEFAULT NULL,
  `underwriting_time` datetime DEFAULT NULL,
  `commercial_policy_no` varchar(100) DEFAULT NULL,
  `compulsory_policy_no` varchar(100) DEFAULT NULL,
  `accept_insurance_remark` varchar(500) DEFAULT NULL,
  `accept_insurance_failed_remark` varchar(500) DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_underwriting_bi` BEFORE INSERT ON `biz_workorder_underwriting` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_biz_workorder_underwriting_bu` BEFORE UPDATE ON `biz_workorder_underwriting` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `biz_workorder_underwriting_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_workorder_underwriting_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `workorder_id` bigint DEFAULT NULL,
  `underwriting_remark` varchar(500) DEFAULT NULL,
  `underwriting_failed_remark` varchar(500) DEFAULT NULL,
  `underwriting_time` datetime DEFAULT NULL,
  `commercial_policy_no` varchar(100) DEFAULT NULL,
  `compulsory_policy_no` varchar(100) DEFAULT NULL,
  `accept_insurance_remark` varchar(500) DEFAULT NULL,
  `accept_insurance_failed_remark` varchar(500) DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monitor_enterprise_daily_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monitor_enterprise_daily_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `enterprise_id` bigint NOT NULL,
  `processed_workorder_count` bigint unsigned NOT NULL DEFAULT '0',
  `request_count` bigint unsigned NOT NULL DEFAULT '0',
  `ocr_count` bigint unsigned NOT NULL DEFAULT '0',
  `new_customer_count` bigint unsigned NOT NULL DEFAULT '0',
  `upstream_income` decimal(18,2) NOT NULL DEFAULT '0.00',
  `downstream_cost` decimal(18,2) NOT NULL DEFAULT '0.00',
  `profit_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `is_finalized` tinyint NOT NULL DEFAULT '0',
  `calculated_at` datetime DEFAULT NULL,
  `finalized_at` datetime DEFAULT NULL,
  `last_flushed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_enterprise_usage_day` (`stat_date`,`enterprise_id`),
  KEY `idx_monitor_enterprise_usage_query` (`enterprise_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monitor_job_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monitor_job_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_type` varchar(32) NOT NULL,
  `batch_no` varchar(64) NOT NULL,
  `stat_date` date DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `processed_count` int unsigned NOT NULL DEFAULT '0',
  `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime NOT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_job_batch` (`batch_no`),
  KEY `idx_monitor_job_type_time` (`job_type`,`started_at`),
  KEY `idx_monitor_job_status_time` (`status`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monitor_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monitor_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_user_id` bigint NOT NULL,
  `action_code` varchar(64) NOT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint NOT NULL,
  `enterprise_id` bigint DEFAULT NULL,
  `before_json` json DEFAULT NULL,
  `after_json` json DEFAULT NULL,
  `reason` varchar(500) NOT NULL,
  `request_id` varchar(100) DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_monitor_operation_operator_time` (`operator_user_id`,`created_at`),
  KEY `idx_monitor_operation_target` (`target_type`,`target_id`,`created_at`),
  KEY `idx_monitor_operation_enterprise_time` (`enterprise_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monitor_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monitor_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `builtin` tinyint NOT NULL DEFAULT '1',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_role_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monitor_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monitor_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(11) NOT NULL COMMENT '登录手机号，唯一登录标识',
  `password_hash` varchar(100) NOT NULL,
  `real_name` varchar(64) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `last_login_at` datetime DEFAULT NULL,
  `password_changed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_username` varchar(11) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `username` else NULL end)) STORED,
  `active_email` varchar(100) GENERATED ALWAYS AS ((case when (`deleted` = 0) then nullif(`email`,_utf8mb4'') else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_user_active_username` (`active_username`),
  UNIQUE KEY `uk_monitor_user_active_email` (`active_email`),
  CONSTRAINT `chk_monitor_user_username_phone` CHECK (regexp_like(`username`,_utf8mb4'^1[0-9]{10}$')),
  KEY `idx_monitor_user_status_created` (`deleted`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monitor_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monitor_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_user_role` (`user_id`,`role_id`),
  KEY `idx_monitor_user_role_role` (`role_id`,`user_id`),
  CONSTRAINT `fk_monitor_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `monitor_role` (`id`),
  CONSTRAINT `fk_monitor_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `monitor_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `saas_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `order_type` varchar(32) DEFAULT NULL,
  `enterprise_id` bigint DEFAULT NULL,
  `buyer_user_id` bigint DEFAULT NULL,
  `plan_id` bigint DEFAULT NULL,
  `plan_snapshot_json` json DEFAULT NULL,
  `buy_user_limit` int DEFAULT NULL,
  `buy_duration_days` int DEFAULT NULL,
  `pay_type` varchar(32) DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `price_amount` decimal(12,2) DEFAULT NULL,
  `discount_amount` decimal(12,2) DEFAULT NULL,
  `credit_amount` decimal(12,2) DEFAULT NULL,
  `payable_amount` decimal(12,2) DEFAULT NULL,
  `refund_amount` decimal(12,2) DEFAULT NULL,
  `paid_amount` decimal(12,2) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `pay_trade_no` varchar(100) DEFAULT NULL,
  `wallet_transaction_id` bigint DEFAULT NULL,
  `original_subscription_id` bigint DEFAULT NULL,
  `old_plan_id` bigint DEFAULT NULL,
  `new_plan_id` bigint DEFAULT NULL,
  `auto_renew` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `failure_reason` varchar(500) DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_order_enterprise_no` (`enterprise_id`,`order_no`),
  KEY `idx_saas_order_enterprise_created` (`enterprise_id`,`deleted`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_order_bi` BEFORE INSERT ON `saas_order` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_order_bu` BEFORE UPDATE ON `saas_order` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `saas_order_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_order_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) DEFAULT NULL,
  `order_type` varchar(32) DEFAULT NULL,
  `enterprise_id` bigint DEFAULT NULL,
  `buyer_user_id` bigint DEFAULT NULL,
  `plan_id` bigint DEFAULT NULL,
  `plan_snapshot_json` json DEFAULT NULL,
  `buy_user_limit` int DEFAULT NULL,
  `buy_duration_days` int DEFAULT NULL,
  `pay_type` varchar(32) DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `price_amount` decimal(12,2) DEFAULT NULL,
  `discount_amount` decimal(12,2) DEFAULT NULL,
  `credit_amount` decimal(12,2) DEFAULT NULL,
  `payable_amount` decimal(12,2) DEFAULT NULL,
  `refund_amount` decimal(12,2) DEFAULT NULL,
  `paid_amount` decimal(12,2) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `pay_trade_no` varchar(100) DEFAULT NULL,
  `wallet_transaction_id` bigint DEFAULT NULL,
  `original_subscription_id` bigint DEFAULT NULL,
  `old_plan_id` bigint DEFAULT NULL,
  `new_plan_id` bigint DEFAULT NULL,
  `auto_renew` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `failure_reason` varchar(500) DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `saas_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `billing_period` varchar(20) DEFAULT NULL,
  `duration_days` int DEFAULT NULL,
  `user_limit` int DEFAULT NULL,
  `price` decimal(12,2) DEFAULT NULL,
  `original_price` decimal(12,2) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `sort_no` int DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=50004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_plan_bi` BEFORE INSERT ON `saas_plan` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_plan_bu` BEFORE UPDATE ON `saas_plan` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `saas_plan_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_plan_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `billing_period` varchar(20) DEFAULT NULL,
  `duration_days` int DEFAULT NULL,
  `user_limit` int DEFAULT NULL,
  `price` decimal(12,2) DEFAULT NULL,
  `original_price` decimal(12,2) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `sort_no` int DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `saas_recharge_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_recharge_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recharge_no` varchar(64) NOT NULL,
  `enterprise_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `pay_trade_no` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_recharge_order_enterprise_no` (`enterprise_id`,`recharge_no`),
  KEY `idx_recharge_paid_rollup` (`deleted`,`status`,`paid_at`,`enterprise_id`),
  KEY `idx_recharge_enterprise_created` (`enterprise_id`,`deleted`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_recharge_order_bi` BEFORE INSERT ON `saas_recharge_order` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_recharge_order_bu` BEFORE UPDATE ON `saas_recharge_order` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `saas_recharge_order_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_recharge_order_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recharge_no` varchar(64) DEFAULT NULL,
  `enterprise_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `pay_trade_no` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `saas_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL,
  `plan_id` bigint DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `user_limit` int NOT NULL DEFAULT '0',
  `ocr_quota` int NOT NULL DEFAULT '0',
  `request_quota` int NOT NULL DEFAULT '0',
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `auto_renew_enabled` tinyint NOT NULL DEFAULT '0',
  `auto_renew_plan_id` bigint DEFAULT NULL,
  `next_renew_at` datetime DEFAULT NULL,
  `last_renew_order_id` bigint DEFAULT NULL,
  `cancel_auto_renew_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_subscription_enterprise` (`enterprise_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_subscription_bi` BEFORE INSERT ON `saas_subscription` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_subscription_bu` BEFORE UPDATE ON `saas_subscription` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `saas_wallet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL,
  `balance_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `frozen_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_wallet_enterprise` (`enterprise_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_wallet_bi` BEFORE INSERT ON `saas_wallet` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_saas_wallet_bu` BEFORE UPDATE ON `saas_wallet` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `saas_wallet_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_wallet_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `balance_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `frozen_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `saas_wallet_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saas_wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `wallet_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `transaction_no` varchar(64) NOT NULL,
  `direction` varchar(10) DEFAULT NULL,
  `transaction_type` varchar(32) DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `balance_before` decimal(12,2) DEFAULT NULL,
  `balance_after` decimal(12,2) DEFAULT NULL,
  `related_order_id` bigint DEFAULT NULL,
  `related_recharge_order_id` bigint DEFAULT NULL,
  `related_subscription_id` bigint DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_wallet_tx_enterprise_no` (`enterprise_id`,`transaction_no`),
  KEY `idx_wallet_tx_enterprise_created` (`enterprise_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `path` varchar(500) DEFAULT NULL,
  `file_name` varchar(100) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `is_linked` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=399 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_sys_file_bi` BEFORE INSERT ON `sys_file` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_sys_file_bu` BEFORE UPDATE ON `sys_file` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `sys_file_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `path` varchar(500) DEFAULT NULL,
  `file_name` varchar(100) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `is_linked` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tenant_enterprise`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_enterprise` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(150) DEFAULT NULL,
  `code` varchar(50) NOT NULL,
  `owner_user_id` bigint DEFAULT NULL,
  `contact_name` varchar(100) DEFAULT NULL,
  `contact_phone` varchar(20) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `source` tinyint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_enterprise_code` (`code`),
  KEY `idx_tenant_enterprise_status_created` (`deleted`,`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_enterprise_bi` BEFORE INSERT ON `tenant_enterprise` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_enterprise_bu` BEFORE UPDATE ON `tenant_enterprise` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `tenant_enterprise_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_enterprise_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(150) DEFAULT NULL,
  `code` varchar(50) DEFAULT NULL,
  `owner_user_id` bigint DEFAULT NULL,
  `contact_name` varchar(100) DEFAULT NULL,
  `contact_phone` varchar(20) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `source` tinyint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tenant_invite_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_invite_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `default_role_code` varchar(32) DEFAULT 'ISSUER',
  `max_use_count` int DEFAULT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `expires_at` datetime DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_invite_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_invite_code_bi` BEFORE INSERT ON `tenant_invite_code` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_invite_code_bu` BEFORE UPDATE ON `tenant_invite_code` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `tenant_invite_code_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_invite_code_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `code` varchar(64) DEFAULT NULL,
  `default_role_code` varchar(32) DEFAULT 'ISSUER',
  `max_use_count` int DEFAULT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `expires_at` datetime DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tenant_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role_code` varchar(32) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `joined_by_invite_id` bigint DEFAULT NULL,
  `joined_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_member_enterprise_user` (`enterprise_id`,`user_id`),
  KEY `idx_tenant_member_enterprise_status` (`enterprise_id`,`deleted`,`status`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_member_bi` BEFORE INSERT ON `tenant_member` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_member_bu` BEFORE UPDATE ON `tenant_member` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `tenant_member_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_member_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `role_code` varchar(32) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `joined_by_invite_id` bigint DEFAULT NULL,
  `joined_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tenant_member_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_member_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `operator_user_id` bigint DEFAULT NULL,
  `target_user_id` bigint NOT NULL,
  `operator_name_snapshot` varchar(100) DEFAULT NULL,
  `target_name_snapshot` varchar(100) DEFAULT NULL,
  `before_role_code` varchar(32) DEFAULT NULL,
  `after_role_code` varchar(32) DEFAULT NULL,
  `invite_id` bigint DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_change_enterprise_time` (`enterprise_id`,`occurred_at`),
  KEY `idx_member_change_enterprise_type` (`enterprise_id`,`event_type`,`occurred_at`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tenant_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL,
  `real_name` varchar(100) DEFAULT NULL,
  `id_num` varchar(100) DEFAULT NULL,
  `avatar_file_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `last_login_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_user_status_created` (`deleted`,`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_user_bi` BEFORE INSERT ON `tenant_user` FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_tenant_user_bu` BEFORE UPDATE ON `tenant_user` FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `tenant_user_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_user_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL,
  `real_name` varchar(100) DEFAULT NULL,
  `id_num` varchar(100) DEFAULT NULL,
  `avatar_file_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `last_login_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `test`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) DEFAULT NULL,
  `create_time` bigint DEFAULT NULL,
  `update_time` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_test_before_insert` BEFORE INSERT ON `test` FOR EACH ROW SET NEW.`create_time` = COALESCE(NEW.`create_time`, UNIX_TIMESTAMP()), NEW.`update_time` = COALESCE(NEW.`update_time`, UNIX_TIMESTAMP());
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
CREATE TRIGGER `trg_test_before_update` BEFORE UPDATE ON `test` FOR EACH ROW SET NEW.`update_time` = UNIX_TIMESTAMP();
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- SaaS core data encoded as UTF-8 hex literals
INSERT INTO auth_permission VALUES (CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x68656C6C6F3A73656C656374 USING utf8mb4),CONVERT(0x68656C6C6F3A73656C656374 USING utf8mb4),NULL,NULL,CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x32 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x757073747265616D2D646F776E73747265616D3A757073747265616D3A73656C656374 USING utf8mb4),CONVERT(0x757073747265616D2D646F776E73747265616D3A757073747265616D3A73656C656374 USING utf8mb4),NULL,NULL,CONVERT(0x32 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x757073747265616D2D646F776E73747265616D3A646F776E73747265616D3A73656C656374 USING utf8mb4),CONVERT(0x757073747265616D2D646F776E73747265616D3A646F776E73747265616D3A73656C656374 USING utf8mb4),NULL,NULL,CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x34 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x616C6C USING utf8mb4),CONVERT(0x616C6C USING utf8mb4),NULL,NULL,CONVERT(0x34 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x35 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x6D65726368616E743A73656C656374 USING utf8mb4),CONVERT(0x6D65726368616E743A73656C656374 USING utf8mb4),NULL,NULL,CONVERT(0x35 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x36 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x6D65726368616E743A757064617465 USING utf8mb4),CONVERT(0x6D65726368616E743A757064617465 USING utf8mb4),NULL,NULL,CONVERT(0x36 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x37 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x776F726B6F726465723A73656C656374 USING utf8mb4),CONVERT(0x776F726B6F726465723A73656C656374 USING utf8mb4),NULL,NULL,CONVERT(0x37 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x38 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x776F726B6F726465723A757064617465 USING utf8mb4),CONVERT(0x776F726B6F726465723A757064617465 USING utf8mb4),NULL,NULL,CONVERT(0x38 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x39 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x757365723A73656C656374 USING utf8mb4),CONVERT(0x757365723A73656C656374 USING utf8mb4),NULL,NULL,CONVERT(0x39 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3130 USING utf8mb4),NULL,CONVERT(0x494E535552414E4345 USING utf8mb4),CONVERT(0x425554544F4E USING utf8mb4),CONVERT(0x757365723A757064617465 USING utf8mb4),CONVERT(0x757365723A757064617465 USING utf8mb4),NULL,NULL,CONVERT(0x3130 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3537 USING utf8mb4),CONVERT(0x30 USING utf8mb4));
INSERT INTO auth_role VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0x41444D494E USING utf8mb4),CONVERT(0xE7AEA1E79086E59198 USING utf8mb4),CONVERT(0x54454E414E54 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0xE794B120696E737572616E63652E726F6C6520E8BF81E7A7BB USING utf8mb4),CONVERT(0x323032362D30312D31302032303A32353A3131 USING utf8mb4),CONVERT(0x323032362D30372D31362031363A30343A3236 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x37 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0xE587BAE58D95E59198 USING utf8mb4),CONVERT(0x54454E414E54 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0xE794B120696E737572616E63652E726F6C6520E8BF81E7A7BB USING utf8mb4),CONVERT(0x323032362D30332D32322031303A30303A3337 USING utf8mb4),CONVERT(0x323032362D30332D32322031303A30303A3337 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x38 USING utf8mb4),CONVERT(0x4F574E4552 USING utf8mb4),CONVERT(0xE4BC81E4B89AE68BA5E69C89E88085 USING utf8mb4),CONVERT(0x54454E414E54 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30372D31372031303A31313A3439 USING utf8mb4),CONVERT(0x323032362D30372D31372031303A31313A3439 USING utf8mb4),CONVERT(0x30 USING utf8mb4));
INSERT INTO auth_role_permission VALUES (CONVERT(0x32 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x323032362D30332D32322031383A32323A3335 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x323032362D30332D32322031383A32323A3335 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x34 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x323032362D30332D32322031383A32323A3335 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x35 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x39 USING utf8mb4),CONVERT(0x323032362D30332D32322031383A32323A3335 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x36 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x37 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x38 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x39 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x34 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3130 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3131 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x36 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3132 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3133 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3134 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x39 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3135 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3130 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30383A3033 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3136 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3137 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3138 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3139 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x34 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3230 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3231 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x36 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3232 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3233 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3234 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x39 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3235 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x3130 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A30373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4));
INSERT INTO tenant_user VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0x3135373632353032323736 USING utf8mb4),CONVERT(0x3135373632353032323736 USING utf8mb4),CONVERT(0x3135373632353032323736403136332E636F6D USING utf8mb4),CONVERT(0x2432612431302436442E68386F3378622E37723831326236686C3662654256436F3355726D6B55727A5A6B614A494D6C5A5059507657444D2F484B69 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0x333730373032323030343036313932363134 USING utf8mb4),NULL,CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30382D30372031323A31383A3131 USING utf8mb4),CONVERT(0x323032362D30312D31302031393A30303A3333 USING utf8mb4),CONVERT(0x323032362D30382D30372031323A31383A3131 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3134 USING utf8mb4),CONVERT(0x3135393635333632353132 USING utf8mb4),CONVERT(0x3135393635333632353132 USING utf8mb4),NULL,CONVERT(0x243261243130246466554777357031503461595A51644A576168794E65475538736D57395931704C5564753370574E496D667136797A324438506F71 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x333730373032323030343036313932363134 USING utf8mb4),NULL,CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3235 USING utf8mb4),CONVERT(0x323032362D30332D32322031313A31383A3136 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3234 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3135 USING utf8mb4),CONVERT(0x3135393635333632353133 USING utf8mb4),CONVERT(0x3135393635333632353133 USING utf8mb4),NULL,CONVERT(0x2432612431302435316C4135497A43595050474E73564436454358614F6942695631794B4A736E58715071304D68316B4C4A2E50764264445069544F USING utf8mb4),CONVERT(0xE69D8EE69D8E USING utf8mb4),CONVERT(0x333730373032323030343036313932363134 USING utf8mb4),NULL,CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30332D32322031333A30323A3532 USING utf8mb4),CONVERT(0x323032362D30332D32322031333A30323A3532 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3235 USING utf8mb4),CONVERT(0x3135373632353032323232 USING utf8mb4),CONVERT(0x3135373632353032323232 USING utf8mb4),CONVERT(0x333034313831313331324071712E636F6D USING utf8mb4),CONVERT(0x24326124313024396D754537583270667557377256686833557243682E38752F7233316656593054447879674C6D4258476436514632575170615057 USING utf8mb4),CONVERT(0xE6B58BE8AF95E982AEE7AEB1 USING utf8mb4),NULL,NULL,CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30342D30312032313A32373A3036 USING utf8mb4),CONVERT(0x323032362D30342D30312032313A32373A3036 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3236 USING utf8mb4),CONVERT(0x3135373230333035393831 USING utf8mb4),CONVERT(0x3135373230333035393831 USING utf8mb4),CONVERT(0x3135373633353032323736403136332E636F6D USING utf8mb4),CONVERT(0x2432612431302473323279356830706C4F335044616C363858552E6C4F4D73526F686C2F5355716B6A54645954534731326A576333454A65507A6871 USING utf8mb4),CONVERT(0xE5B08FE78E8B USING utf8mb4),NULL,NULL,CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30342D30312032313A33303A3038 USING utf8mb4),CONVERT(0x323032362D30342D30312032313A33303A3038 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3237 USING utf8mb4),CONVERT(0x3133333435323532393833 USING utf8mb4),CONVERT(0x3133333435323532393833 USING utf8mb4),NULL,CONVERT(0x243261243130245872374F30346C6969752E4A6D5778427758784A657565594B4C3841362E7831374C7443507952372E46302F49626F446C71783957 USING utf8mb4),CONVERT(0x6C656D6F6E USING utf8mb4),NULL,NULL,CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30343A3430 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30343A3333 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30343A3339 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4));
INSERT INTO tenant_enterprise VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0xE5B08F65E4BF9DE5AD98E9878FE4B89AE58AA1 USING utf8mb4),CONVERT(0x4C45474143595F494E535552414E4345 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x6C656D6F6E USING utf8mb4),CONVERT(0x3135373632353032323736 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x323032362D30372D31352031303A30373A3531 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33353A3338 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x32 USING utf8mb4),CONVERT(0xE69D8EE6A285E6B58BE8AF95E4BC81E4B89A USING utf8mb4),CONVERT(0x454E542D32303236303732303132333934323830312D424E544850 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x3135393635333632353132 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),CONVERT(0xE6B58BE8AF95 USING utf8mb4),CONVERT(0x454E542D32303236303732353231303530343135342D3950435132 USING utf8mb4),CONVERT(0x3237 USING utf8mb4),CONVERT(0x6C656D6F6E USING utf8mb4),CONVERT(0x3133333435323532393833 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4));
INSERT INTO tenant_member VALUES (CONVERT(0x3139 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33383A3530 USING utf8mb4),CONVERT(0x323032362D30332D32322031313A31383A3136 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3238 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x31 USING utf8mb4)),(CONVERT(0x3230 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3135 USING utf8mb4),CONVERT(0x41444D494E USING utf8mb4),CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30332D32322031333A30323A3532 USING utf8mb4),CONVERT(0x323032362D30332D32322031333A30323A3532 USING utf8mb4),CONVERT(0x323032362D30372D31372031383A30303A3535 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3332 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3235 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x30 USING utf8mb4),NULL,CONVERT(0x323032362D30342D30312032313A32373A3036 USING utf8mb4),CONVERT(0x323032362D30342D30312032313A32373A3036 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33323A3536 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4)),(CONVERT(0x3336 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4F574E4552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30312D31302031393A30303A3333 USING utf8mb4),CONVERT(0x323032362D30312D31302031393A30303A3333 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33353A3338 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3337 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3236 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30342D30312032313A33303A3038 USING utf8mb4),CONVERT(0x323032362D30342D30312032313A33303A3038 USING utf8mb4),CONVERT(0x323032362D30372D31372031363A35313A3433 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3338 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x4F574E4552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A34303A3032 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3339 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x3237 USING utf8mb4),CONVERT(0x4F574E4552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3334 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4));
INSERT INTO tenant_invite_code VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x584D45422D58334A34334E4C56 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x323032362D30382D30312032333A35393A3539 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31372031303A35333A3438 USING utf8mb4),CONVERT(0x323032362D30372D31372031303A35343A3038 USING utf8mb4),CONVERT(0x31 USING utf8mb4)),(CONVERT(0x32 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x584D45422D514435434D533436 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31382032333A35393A3539 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31372031373A30373A3336 USING utf8mb4),CONVERT(0x323032362D30372D31372031373A30393A3538 USING utf8mb4),CONVERT(0x31 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x584D45422D383856475A355841 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31382032333A35393A3539 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31372031373A31353A3337 USING utf8mb4),CONVERT(0x323032362D30372D31372031373A31373A3433 USING utf8mb4),CONVERT(0x31 USING utf8mb4)),(CONVERT(0x34 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x584D45422D3557594351563936 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x323032362D30372D31382032333A35393A3539 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31372031373A31373A3438 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33383A3334 USING utf8mb4),CONVERT(0x31 USING utf8mb4)),(CONVERT(0x35 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x584D45422D3355374D33483248 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32312032333A35393A3539 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33383A3237 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33383A3530 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x36 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x584D45422D575447545A505752 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x323032362D30372D32362032333A35393A3539 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30333A3038 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30333A3038 USING utf8mb4),CONVERT(0x30 USING utf8mb4));
INSERT INTO tenant_member_change_log VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4F574E45525F5452414E53464552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),NULL,CONVERT(0x4F574E4552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D31372031373A31383A3536 USING utf8mb4),CONVERT(0xE997A8E688B7E4B8BBE58AA8E8BDACE8AEA9 USING utf8mb4)),(CONVERT(0x32 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4F574E45525F5452414E53464552 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),NULL,CONVERT(0x4F574E4552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D31372031373A31393A3531 USING utf8mb4),CONVERT(0xE997A8E688B7E4B8BBE58AA8E8BDACE8AEA9 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4B49434B USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x41444D494E USING utf8mb4),NULL,NULL,CONVERT(0x323032362D30372D31372031383A30323A3234 USING utf8mb4),CONVERT(0xE7A7BBE587BAE4BC81E4B89AE68890E59198 USING utf8mb4)),(CONVERT(0x34 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4A4F494E USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),NULL,CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x34 USING utf8mb4),CONVERT(0x323032362D30372D31372031383A30333A3533 USING utf8mb4),CONVERT(0xE9809AE8BF87E98280E8AFB7E7A081E58AA0E585A5E4BC81E4B89A USING utf8mb4)),(CONVERT(0x35 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4F574E45525F5452414E53464552 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x4F574E4552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32302031323A31303A3338 USING utf8mb4),CONVERT(0xE997A8E688B7E4B8BBE58AA8E8BDACE8AEA9E4BC81E4B89AE68BA5E69C89E88085 USING utf8mb4)),(CONVERT(0x36 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4B49434B USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3235 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0xE6B58BE8AF95E982AEE7AEB1 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),NULL,NULL,CONVERT(0x323032362D30372D32302031323A33323A3536 USING utf8mb4),CONVERT(0xE7A7BBE587BAE4BC81E4B89AE68890E59198 USING utf8mb4)),(CONVERT(0x37 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4F574E45525F5452414E53464552 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0x41444D494E USING utf8mb4),CONVERT(0x4F574E4552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32302031323A33353A3338 USING utf8mb4),CONVERT(0xE997A8E688B7E4B8BBE58AA8E8BDACE8AEA9E4BC81E4B89AE68BA5E69C89E88085 USING utf8mb4)),(CONVERT(0x38 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x524F4C455F4348414E4745 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE9B8A3 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x41444D494E USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32302031323A33373A3032 USING utf8mb4),CONVERT(0xE4BFAEE694B9E4BC81E4B89AE68890E59198E8A792E889B2 USING utf8mb4)),(CONVERT(0x39 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x45584954 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),NULL,NULL,CONVERT(0x323032362D30372D32302031323A33373A3435 USING utf8mb4),CONVERT(0xE68890E59198E4B8BBE58AA8E98080E587BAE4BC81E4B89A USING utf8mb4)),(CONVERT(0x3130 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x4A4F494E USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),NULL,CONVERT(0x495353554552 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33383A3530 USING utf8mb4),CONVERT(0xE9809AE8BF87E98280E8AFB7E7A081E58AA0E585A5E4BC81E4B89A USING utf8mb4)),(CONVERT(0x3131 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x45584954 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0x495353554552 USING utf8mb4),NULL,NULL,CONVERT(0x323032362D30372D32302031323A33393A3238 USING utf8mb4),CONVERT(0xE68890E59198E4B8BBE58AA8E98080E587BAE4BC81E4B89A USING utf8mb4)),(CONVERT(0x3132 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x4A4F494E USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),CONVERT(0xE69D8EE6A285 USING utf8mb4),NULL,CONVERT(0x4F574E4552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),CONVERT(0xE5889BE5BBBAE4BC81E4B89AE5B9B6E58AA0E585A5 USING utf8mb4)),(CONVERT(0x3133 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x4A4F494E USING utf8mb4),CONVERT(0x3237 USING utf8mb4),CONVERT(0x3237 USING utf8mb4),CONVERT(0x6C656D6F6E USING utf8mb4),CONVERT(0x6C656D6F6E USING utf8mb4),NULL,CONVERT(0x4F574E4552 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),CONVERT(0xE5889BE5BBBAE4BC81E4B89AE5B9B6E58AA0E585A5 USING utf8mb4));
INSERT INTO saas_plan VALUES (CONVERT(0x3530303031 USING utf8mb4),CONVERT(0x535441525445525F4D4F4E5448 USING utf8mb4),CONVERT(0xE8BDBBE9878FE78988 USING utf8mb4),CONVERT(0xE98082E59088E5B08FE59BA2E9989FE8B5B7E6ADA5EFBC8CE8A686E79B96E59FBAE7A180E68890E59198E58D8FE4BD9CE5928CE8BDA6E999A9E5B7A5E58D95E5A484E79086E38082 USING utf8mb4),CONVERT(0x4D4F4E5448 USING utf8mb4),CONVERT(0x3330 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x3239392E3030 USING utf8mb4),CONVERT(0x3339392E3030 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31362031383A30383A3037 USING utf8mb4),CONVERT(0x323032362D30372D31362031383A30383A3037 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3530303032 USING utf8mb4),CONVERT(0x50524F5F59454152 USING utf8mb4),CONVERT(0xE4B893E4B89AE78988 USING utf8mb4),CONVERT(0xE98082E59088E7A8B3E5AE9AE7BB8FE890A5E59BA2E9989FEFBC8CE694AFE68C81E69BB4E5A49AE68890E59198E38081E7BBADE4BF9DE8B79FE8BF9BE5928CE8B4A2E58AA1E5AFB9E8B4A6E38082 USING utf8mb4),CONVERT(0x59454152 USING utf8mb4),CONVERT(0x333635 USING utf8mb4),CONVERT(0x3330 USING utf8mb4),CONVERT(0x323939392E3030 USING utf8mb4),CONVERT(0x333939392E3030 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x323032362D30372D31362031383A30383A3037 USING utf8mb4),CONVERT(0x323032362D30372D31362031383A30383A3037 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4)),(CONVERT(0x3530303033 USING utf8mb4),CONVERT(0x454E54455250524953455F59454152 USING utf8mb4),CONVERT(0xE4BC81E4B89AE78988 USING utf8mb4),CONVERT(0xE98082E59088E5A49AE7BD91E782B9E4BC81E4B89AEFBC8CE68F90E4BE9BE69BB4E9AB98E68890E59198E4B88AE99990E5928CE4B893E5B19EE69C8DE58AA1E694AFE68C81E38082 USING utf8mb4),CONVERT(0x59454152 USING utf8mb4),CONVERT(0x333635 USING utf8mb4),CONVERT(0x313030 USING utf8mb4),CONVERT(0x383939392E3030 USING utf8mb4),CONVERT(0x31303939392E3030 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x323032362D30372D31362031383A30383A3037 USING utf8mb4),CONVERT(0x323032362D30372D31362031383A30383A3037 USING utf8mb4),NULL,CONVERT(0x30 USING utf8mb4));
INSERT INTO saas_subscription VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3530303033 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x313030 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x323032362D30372D31372031393A31313A3330 USING utf8mb4),CONVERT(0x323032382D30372D31362031393A31313A3330 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3530303033 USING utf8mb4),CONVERT(0x323032382D30372D31362031393A31313A3330 USING utf8mb4),CONVERT(0x33 USING utf8mb4),NULL,CONVERT(0x323032362D30372D31372031313A34343A3436 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30333A3238 USING utf8mb4)),(CONVERT(0x32 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x3530303031 USING utf8mb4),CONVERT(0x34 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A34303A3033 USING utf8mb4),CONVERT(0x323032362D30382D31392031323A34303A3033 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3530303031 USING utf8mb4),CONVERT(0x323032362D30382D31392031323A34303A3033 USING utf8mb4),CONVERT(0x34 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A34303A3032 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x3530303032 USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3330 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3335 USING utf8mb4),CONVERT(0x323032372D30372D32352032313A30353A3335 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x3530303032 USING utf8mb4),CONVERT(0x323032372D30372D32352032313A30353A3335 USING utf8mb4),CONVERT(0x35 USING utf8mb4),NULL,CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3334 USING utf8mb4));
INSERT INTO saas_wallet VALUES (CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x302E3030 USING utf8mb4),CONVERT(0x302E3030 USING utf8mb4),CONVERT(0x434E59 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D31372031363A35303A3034 USING utf8mb4),CONVERT(0x323032362D30372D31372031393A31313A3239 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x32 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x302E3030 USING utf8mb4),CONVERT(0x302E3030 USING utf8mb4),CONVERT(0x434E59 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A33393A3432 USING utf8mb4),CONVERT(0x323032362D30372D32302031323A34303A3032 USING utf8mb4),CONVERT(0x3134 USING utf8mb4),CONVERT(0x30 USING utf8mb4)),(CONVERT(0x33 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x323030312E3030 USING utf8mb4),CONVERT(0x302E3030 USING utf8mb4),CONVERT(0x434E59 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3034 USING utf8mb4),CONVERT(0x323032362D30372D32352032313A30353A3334 USING utf8mb4),CONVERT(0x3237 USING utf8mb4),CONVERT(0x30 USING utf8mb4));
