-- ====================================================================
-- HIVE DATA DEFINITION LANGUAGE (DDL) FOR POLICY ANALYTICS PLATFORM
-- ====================================================================
-- This script creates the final tables for analysts and BI tools.
-- These tables are populated by the final Spark aggregation job.

-- Set the database context
-- CREATE DATABASE IF NOT EXISTS analytics;
-- USE analytics;

-- ====================================================================
-- DIMENSION TABLES
-- ====================================================================

-- --------------------------------------------------------------------
-- dim_customer: The "Single Source of Truth" for customer information.
-- This table holds the latest, cleansed, and deduplicated record for each customer.
-- --------------------------------------------------------------------
CREATE EXTERNAL TABLE IF NOT EXISTS dim_customer (
  customer_id               BIGINT COMMENT 'Unique identifier for the customer',
  full_name                 STRING COMMENT 'Customer full name',
  gender                    STRING COMMENT 'Customer gender (MALE, FEMALE, UNKNOWN)',
  email                     STRING COMMENT 'Customer contact email address',
  street_address            STRING COMMENT 'Customer street address',
  city                      STRING COMMENT 'Customer city',
  state_province            STRING COMMENT 'Customer state or province',
  postal_code               STRING COMMENT 'Customer postal code',
  country                   STRING COMMENT 'Customer country',
  date_of_birth             DATE   COMMENT 'Customer date of birth'
)
COMMENT 'Dimension table containing unique, clean customer records.'
STORED AS PARQUET
LOCATION '/user/hive/warehouse/analytics/dim_customer';


-- ====================================================================
-- FACT TABLES
-- ====================================================================

-- --------------------------------------------------------------------
-- fact_policy_transactions: The central fact table.
-- Contains one row per policy, representing the most current state of that policy.
-- This is the primary table for detailed, ad-hoc analysis.
-- --------------------------------------------------------------------
CREATE EXTERNAL TABLE IF NOT EXISTS fact_policy_transactions (
  policy_id                 BIGINT COMMENT 'Unique identifier for the policy',
  customer_id               BIGINT COMMENT 'Foreign key to the dim_customer table',
  coverage_id               BIGINT COMMENT 'Identifier for the type of coverage',
  policy_status             STRING COMMENT 'Current status of the policy (e.g., ACTIVE, EXPIRED, PENDING)',
  premium_amount            DECIMAL(12, 2) COMMENT 'The premium amount for the policy term',
  effective_date            DATE   COMMENT 'The date the policy becomes effective',
  end_date                  DATE   COMMENT 'The date the policy expires',
  last_update_timestamp     TIMESTAMP COMMENT 'Timestamp of the last update from the source system'
)
COMMENT 'Core fact table containing the current state of all policies.'
PARTITIONED BY (processing_date DATE COMMENT 'The date the ETL job processed this record, used for partitioning.')
STORED AS PARQUET
LOCATION '/user/hive/warehouse/analytics/fact_policy_transactions';


-- ====================================================================
-- AGGREGATED DATA MARTS
-- ====================================================================

-- --------------------------------------------------------------------
-- agg_daily_policy_summary: A pre-aggregated data mart for fast dashboarding.
-- This table provides a daily summary of key policy metrics, avoiding the need
-- to scan the large fact table for common reporting needs.
-- --------------------------------------------------------------------
CREATE EXTERNAL TABLE IF NOT EXISTS agg_daily_policy_summary (
  policy_status             STRING COMMENT 'Status of policies in this summary group',
  total_policies            BIGINT COMMENT 'Total number of policies for this status and day',
  total_premium_value       DECIMAL(18, 2) COMMENT 'Sum of premiums for all policies in this group',
  average_premium           DECIMAL(12, 2) COMMENT 'Average premium for policies in this group',
  distinct_customer_count   BIGINT COMMENT 'Count of unique customers in this group'
)
COMMENT 'Daily aggregated summary of policy metrics for high-performance reporting.'
PARTITIONED BY (summary_date DATE COMMENT 'The date for which the summary is calculated.')
STORED AS PARQUET
LOCATION '/user/hive/warehouse/analytics/agg_daily_policy_summary';


-- ====================================================================
-- POST-CREATION STEPS
-- ====================================================================
-- After the Spark jobs run and populate the HDFS locations, you must
-- add the partitions to the Hive Metastore so they become queryable.
-- This is typically done as the last step in the ETL workflow.
--
-- Example command to be run after the daily pipeline succeeds:
--
-- MSCK REPAIR TABLE fact_policy_transactions;
-- MSCK REPAIR TABLE agg_daily_policy_summary;
--
-- Or, more explicitly for a given day's run:
--
-- ALTER TABLE fact_policy_transactions ADD IF NOT EXISTS PARTITION (processing_date='2018-01-02');
-- ALTER TABLE agg_daily_policy_summary ADD IF NOT EXISTS PARTITION (summary_date='2018-01-02');
-- ====================================================================
